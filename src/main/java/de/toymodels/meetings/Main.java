package de.toymodels.meetings;

import net.sourceforge.nite.meta.impl.NiteMetaData;
import net.sourceforge.nite.meta.impl.NiteObservation;
import net.sourceforge.nite.nom.nomwrite.NOMAttribute;
import net.sourceforge.nite.nom.nomwrite.NOMElement;
import net.sourceforge.nite.nom.nomwrite.NOMPointer;
import net.sourceforge.nite.nom.nomwrite.impl.NOMWriteAnnotation;
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

    private static final List<String> ICSI_TRAIN = List.of(
            "Bdb001", "Bed002", "Bed003", "Bed005", "Bed006", "Bed008", "Bed010",
            "Bed011", "Bed012", "Bed013", "Bed014", "Bed015", "Bed017", "Bmr003",
            "Bmr006", "Bmr007", "Bmr009", "Bmr010", "Bmr011", "Bmr012", "Bmr013",
            "Bmr014", "Bmr015", "Bmr016", "Bmr020", "Bmr023", "Bmr024", "Bmr025",
            "Bmr026", "Bmr027", "Bro003", "Bro004", "Bro005", "Bro007", "Bro010",
            "Bro011", "Bro012", "Bro013", "Bro014", "Bro015", "Bro016", "Bro017",
            "Bro019", "Bro021", "Bro022", "Bro023", "Bro024", "Bro027", "Bro028");

    private static final List<String> ICSI_DEV = List.of("Bmr018", "Bns001", "Bro008", "Bro025", "Bro026", "Buw001");

    private static final List<String> ICSI_TEST = List.of("Bed004", "Bed009", "Bed016", "Bmr005", "Bmr019", "Bro018");

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
     * A simple POJO for topics.
     */
    static class Topic {

        private final String scenarioTopicType;
        private final String description;
        private final String otherDescription;

        private final String text;

        public Topic(String scenarioTopicType, String description, String otherDescription, String text) {
            this.scenarioTopicType = scenarioTopicType;
            this.description = description;
            this.otherDescription = otherDescription;
            this.text = text;
        }

        public String getScenarioTopicType() {
            return scenarioTopicType;
        }

        public String getDescription() {
            return description;
        }

        public String getOtherDescription() {
            return otherDescription;
        }

        public String getText() {
            return text;
        }
    }

    /**
     * Generates dev, test and train datasets for the given data.
     *
     * @param args The first element should be a path to a NXT metadata file.
     * @throws Exception If something went wrong.
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Wrong amount of arguments! The following arguments are expected:");
            System.err.println("1st arg: A path a NXT metadata file");
            System.err.println("2nd arg: The type of processing. Please read the README for valid types!");
            System.exit(-1);
            return;
        }

        String metaDataFileName = args[0];
        String processingType = args[1];

        if (!processingType.equals("1") && !processingType.equals("2") && !processingType.equals("3")) {
            System.err.println("Invalid processing type (2nd arg) provided! Please read the README for valid types!");
            System.exit(-1);
            return;
        }

        NiteMetaData niteMetaData = new NiteMetaData(metaDataFileName);
        List<NiteObservation> observations = convertToGenericList(niteMetaData.getObservations(), NiteObservation.class);

        Map<String, DataType> split = getDataSplit(niteMetaData.getCorpusResourcePath() + "/meetings.xml");

        switch (processingType) {
            case "1":
                processSummlinks(niteMetaData, observations, split);
                break;
            case "2":
                processTopics(niteMetaData, observations, split);
                break;
            case "3":
                processSummaries(niteMetaData, observations, split);
                break;
            default:
                System.err.println("Unknown processing type!");
                System.exit(-1);
        }
    }

    /**
     * Writes the topics to files.
     *
     * @param niteMetaData The nite meta data.
     * @param observations A list with observations.
     * @param split The split for meetings.
     * @throws Exception If something went wrong.
     */
    private static void processTopics(NiteMetaData niteMetaData, List<NiteObservation> observations, Map<String, DataType> split) throws Exception {
        for (NiteObservation observation : observations) {
            List<Topic> topics = getTopics(niteMetaData, observation);

            if (topics == null) {
                continue;
            }

            DataType dataType = split.get(observation.getShortName());

            if (dataType == null) {
                dataType = getIcsiDataType(observation.getShortName());
            }

            StringBuilder fileContent = new StringBuilder();
            topics.forEach(topic -> fileContent.append(topic.getText()).append("\n"));

            Files.write(Paths.get("topcis." + observation.getShortName() + "." + dataType.name().toLowerCase() + ".txt"), fileContent.toString().getBytes());
        }
    }

    /**
     * Writes the summaries to files.
     *
     * @param niteMetaData The nite meta data.
     * @param observations A list with observations.
     * @param split The split for meetings.
     * @throws Exception If something went wrong.
     */
    private static void processSummaries(NiteMetaData niteMetaData, List<NiteObservation> observations, Map<String, DataType> split) throws Exception {
        for (NiteObservation observation : observations) {
            String summary = getSummary(niteMetaData, observation);

            if (summary == null || summary.isEmpty()) {
                continue;
            }

            DataType dataType = split.get(observation.getShortName());

            if (dataType == null) {
                dataType = getIcsiDataType(observation.getShortName());
            }

            Files.write(Paths.get("summaries." + observation.getShortName() + "." + dataType.name().toLowerCase() + ".txt"), summary.getBytes());
        }
    }

    /**
     * Uses the summlinks to create data pairs that map dialogue acts to sentences from the extractive summaries.
     *
     * @param niteMetaData The nite meta data.
     * @param observations A list with observations.
     * @param split The split for meetings.
     * @throws Exception If something went wrong.
     */
    private static void processSummlinks(NiteMetaData niteMetaData, List<NiteObservation> observations, Map<String, DataType> split) throws Exception {
        // Group by data type
        HashMap<DataType, List<DataPair>> data = new HashMap<>();

        for (NiteObservation observation : observations) {
            List<DataPair> dataPairs = getDataPairsForObservation(niteMetaData, observation);

            if (dataPairs == null) {
                continue;
            }

            DataType dataType = split.get(observation.getShortName());

            if (dataType == null) {
                dataType = getIcsiDataType(observation.getShortName());
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

    private static String getSummary(NiteMetaData niteMetaData, NiteObservation observation) throws Exception {
        NOMWriteCorpus nomCorpus = new NOMWriteCorpus(niteMetaData);
        nomCorpus.loadData(observation);

        List<?> ungenericAbstract = nomCorpus.getElementsByName("abstract");
        if (ungenericAbstract == null) {
            return null;
        }

        List<NOMWriteAnnotation> abstracts = convertToGenericList(ungenericAbstract, NOMWriteAnnotation.class);
        if (abstracts.size() != 1) {
            throw new IllegalStateException("Meeting with more than one abstract!");
        }

        List<?> ungenericSentences = abstracts.get(0).getChildren();
        if (ungenericSentences == null) {
            return null;
        }

        List<NOMWriteAnnotation> sentences = convertToGenericList(ungenericSentences, NOMWriteAnnotation.class);

        StringBuilder fullAbstract = new StringBuilder();
        sentences.forEach(sentence -> fullAbstract.append(sentence.getText()).append(" "));
        return fullAbstract.toString();
    }

    /**
     * Gets a list with topics for the given observation.
     *
     * @param niteMetaData The nite meta data.
     * @param observation The observation.
     * @return A list of topics for the given observation. Can be null if there are no topics.
     * @throws Exception If something went wrong.
     */
    private static List<Topic> getTopics(NiteMetaData niteMetaData, NiteObservation observation) throws Exception {
        List<Topic> topicList = new ArrayList<>();

        NOMWriteCorpus nomCorpus = new NOMWriteCorpus(niteMetaData);
        nomCorpus.loadData(observation);

        List<?> ungenericTopics = nomCorpus.getElementsByName("topic");
        if (ungenericTopics == null) {
            return null;
        }

        List<NOMWriteAnnotation> topics = convertToGenericList(ungenericTopics, NOMWriteAnnotation.class);
        for (NOMWriteAnnotation topic : topics) {

            NOMPointer scenarioTopicTypePointer = topic.getPointerWithRole("scenario_topic_type");
            String scenarioTopicType = null;
            if (scenarioTopicTypePointer != null) {
                scenarioTopicType = scenarioTopicTypePointer.getToElement().getAttribute("name").getStringValue();
            }

            NOMAttribute descriptionAttribute = topic.getAttribute("description");
            String description = null;
            if (descriptionAttribute != null) {
                description = descriptionAttribute.getStringValue();
            }

            NOMAttribute otherDescriptionAttribute = topic.getAttribute("other_description");
            String otherDescription = null;
            if (otherDescriptionAttribute != null) {
                otherDescription = otherDescriptionAttribute.getStringValue();
            }

            List<?> ungenericWords = topic.getChildren();
            if (ungenericWords == null) {
                continue;
            }

            List<NOMElement> words = convertToGenericList(ungenericWords, NOMElement.class);
            StringBuilder builder = new StringBuilder();
            collectWords(words, builder);

            if (builder.length() <= 0) {
                continue;
            }

            topicList.add(new Topic(scenarioTopicType, description, otherDescription, builder.toString()));
        }

        return topicList;
    }

    /**
     * Takes a list of words and appends them to the given string builder.
     * <p>
     * Does some additional processing like dropping some unwanted words.
     *
     * @param words The words.
     * @param builder The string builder.
     */
    private static void collectWords(List<NOMElement> words, StringBuilder builder) {
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
            if (builder.toString().split(" ").length > 250) {
                return;
            }
            // Add a space when the word is no punctuation mark and there was a word before
            if (word.getAttribute("punc") == null && builder.length() > 0) {
                builder.append(" ");
            }
            builder.append(word.getText());
        });
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
            collectWords(words, builder);
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

    /**
     * Gets the hardcoded data type of a meeting of the ICSI data set.
     *
     * @param shortName The short name of the meeting.
     * @return The data type of the observation.
     */
    private static DataType getIcsiDataType(String shortName) {
        if (ICSI_TRAIN.contains(shortName)) {
            return DataType.TRAIN;
        } else if (ICSI_DEV.contains(shortName)) {
            return DataType.DEV;
        } else if (ICSI_TEST.contains(shortName)) {
            return DataType.TEST;
        } else {
            throw new IllegalArgumentException("Observation with unknown datatype");
        }
    }

}
