package de.toymodels.meetings;

import net.sourceforge.nite.meta.impl.NiteMetaData;
import net.sourceforge.nite.meta.impl.NiteObservation;
import net.sourceforge.nite.nom.nomwrite.NOMElement;
import net.sourceforge.nite.nom.nomwrite.impl.NOMWriteCorpus;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    // Words that we drop, as they don't add any meaningful value to the sentences
    // See http://groups.inf.ed.ac.uk/ami/corpus/regularised_spellings.shtml
    private static final List<String> WORDS_TO_DROP = List.of(
            "ah", "huh", "hmm", "mm", // BACKCHANNELS
            "uh", "um", "mm", // HESITATIONS
            "eh", "huh"); // TAG QUESTIONS

    enum DataType {
        TRAIN, DEV, TEST
    }

    /**
     * A simple POJO for data pairs.
     */
    static class DataPair {

        private final String input;
        private final String output;

        public DataPair(String input, String output) {
            this.input = input;
            this.output = output;
        }

        public String getInput() {
            return input;
        }

        public String getOutput() {
            return output;
        }

    }

    /**
     * Generates dev, test and train datasets for the given data.
     *
     * @param args The first element should be a path to a NXT metadata file.
     * @throws Exception If something went wrong.
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Please provide exactly one argument with the path to a NXT metadata file.");
            return;
        }
        String metaDataFileName = args[0];

        NiteMetaData niteMetaData = new NiteMetaData(metaDataFileName);
        List<NiteObservation> observations = convertToGenericList(niteMetaData.getObservations(), NiteObservation.class);

        Map<String, DataType> split = getDataSplit(niteMetaData.getCorpusResourcePath() + "/meetings.xml");

        // Group by data type
        HashMap<DataType, List<DataPair>> data = new HashMap<>();

        for (NiteObservation observation : observations) {
            List<DataPair> dataPairs = getDataPairsForObservation(niteMetaData, observation);

            if (dataPairs == null) {
                continue;
            }

            DataType dataType = split.get(observation.getShortName());

            if (dataType == null) {
                System.out.println("[WARN] Unknown data type for " + observation.getShortName() + ". Using TRAIN as fallback!");
                dataType = DataType.TRAIN;
            }

            data.computeIfAbsent(dataType, k -> new ArrayList<>()).addAll(dataPairs);
        }

        // Create a tsv file for every data type
        for (Map.Entry<DataType, List<DataPair>> entry : data.entrySet()) {
            StringBuilder fileContent = new StringBuilder();
            for (DataPair dataPair : entry.getValue()) {
                fileContent.append(dataPair.getInput()).append("\t");
                fileContent.append(dataPair.getOutput()).append("\n");
            }

            Files.write(Paths.get("data." + entry.getKey().name().toLowerCase() + ".tsv"), fileContent.toString().getBytes());
        }
    }

    /**
     * Parses a meeting.xml to extract the information if a meeting should be used for train, dev or test.
     * <p>
     * See http://groups.inf.ed.ac.uk/ami/corpus/datasets.shtml
     * <p>
     * <b>Implementation note:</b>
     * This method parses the xml file directly using dom4j and does not use the NXT toolkit.
     * Maybe there is an easier option by using the NXT api, but I wasn't able to find anything.
     *
     * @param filePath The path to the meeting.xml file.
     * @return A map that has the meeting name (e.g. ES2003c) as key and it's data type as value (e.g. DEV).
     */
    private static Map<String, DataType> getDataSplit(String filePath) {
        Map<String, DataType> split = new HashMap<>();

        SAXReader reader = new SAXReader();

        Document document;
        try {
            document = reader.read(filePath);
        } catch (DocumentException e) {
            System.out.println("[WARN] Cannot find meetings.xml file. Cannot determine data split!");
            return split;
        }

        for (Element element : document.getRootElement().elements()) {
            String type = element.attribute("type").getValue();
            if (type.equals("scenario")) {
                String observation = element.attribute("observation").getValue();
                String visibility = element.attribute("visibility").getValue();

                // TRAIN: visibility = seen, seen_type = training
                // DEV: visibility = seen, seen_type = development
                // TEST: visibility = unseen, seen_type = null
                DataType dataType = DataType.TEST;
                if (visibility.equals("seen")) {
                    String seenType = element.attribute("seen_type").getValue();
                    switch (seenType) {
                        case "training":
                            dataType = DataType.TRAIN;
                            break;
                        case "development":
                            dataType = DataType.DEV;
                            break;
                        default:
                            throw new IllegalStateException("Encountered unknown seen_type " + seenType);
                    }
                }

                split.put(observation, dataType);
            }
        }

        return split;
    }

    /**
     * Gets a list of data pairs for the given observation.
     *
     * @param niteMetaData The nite meta data.
     * @param observation The observation.
     * @return A list of data pairs for the given observation. Can be null if there is no summlink.
     * @throws Exception If something went wrong.
     */
    private static List<DataPair> getDataPairsForObservation(NiteMetaData niteMetaData, NiteObservation observation) throws Exception {
        List<DataPair> dataPairs = new ArrayList<>();

        NOMWriteCorpus nomCorpus = new NOMWriteCorpus(niteMetaData);
        nomCorpus.loadData(observation);

        List<?> ungenericSummLinks = nomCorpus.getElementsByName("summlink");
        if (ungenericSummLinks == null) {
            // Only the scenario meetings have a summlink
            System.out.println("Skipping " + observation.getShortName() + " because summlink is missing!");
            return null;
        }

        HashMap<String, String> abstractiveIdToSummarization = new HashMap<>();
        HashMap<String, String> abstractiveIdIdToTranscript = new HashMap<>();

        List<NOMElement> summLinks = convertToGenericList(ungenericSummLinks, NOMElement.class);
        for (NOMElement summLink : summLinks) {
            List<?> ungenericWords = summLink.getPointerWithRole("extractive").getToElement().getChildren();
            if (ungenericWords == null) {
                System.out.println("Skipping " + summLink.getName() + " because their extractive has no children");
                continue;
            }
            List<NOMElement> words = convertToGenericList(ungenericWords, NOMElement.class);
            NOMElement abstractive = summLink.getPointerWithRole("abstractive").getToElement();

            abstractiveIdToSummarization.put(abstractive.getID(), abstractive.getText());
            StringBuilder builder = new StringBuilder();
            builder.append(abstractiveIdIdToTranscript.getOrDefault(abstractive.getID(), ""));
            words.forEach(word -> {
                // Some words have no text for some reason
                if (word.getText() == null) {
                    return;
                }
                // Some words should be dropped, as they don't add any meaningful value to the sentence
                if (WORDS_TO_DROP.contains(word.getText().toLowerCase())) {
                    return;
                }
                // Addresses https://github.com/asyml/texar/issues/264
                if (builder.toString().split(" ").length > 350) {
                    return;
                }
                // Add a space when the word is no punctuation mark and there was a word before
                if (word.getAttribute("punc") == null && builder.length() > 0) {
                    builder.append(" ");
                }
                builder.append(word.getText());
            });
            abstractiveIdIdToTranscript.put(abstractive.getID(), builder.toString());
        }

        abstractiveIdIdToTranscript.keySet().forEach(absId ->
            dataPairs.add(new DataPair(abstractiveIdIdToTranscript.get(absId), abstractiveIdToSummarization.get(absId)))
        );

        return dataPairs;
    }

    /**
     * Creates a generic copy of the given list.
     *
     * @param list The non-generic list.
     * @param type The type of the new generic list.
     * @param <T> The type for the new list.
     * @return A generic copy of the given list.
     * @throws IllegalArgumentException If any of the list's elements is not an instance of {@code type}.
     */
    @SuppressWarnings("unchecked")
    private static <T> List<T> convertToGenericList(List<?> list, Class<T> type) {
        List<T> newList = new ArrayList<>();
        for (Object element : list) {
            if (type.isInstance(element)) {
                newList.add((T) element);
            } else {
                throw new IllegalArgumentException("The provided list does contain elements that are not of type " + type.getName());
            }
        }
        return newList;
    }

}
