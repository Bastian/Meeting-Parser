# AMI Meeting Parser

A simple Java program that parses the [AMI meeting corpus](http://groups.inf.ed.ac.uk/ami/corpus/).

It is used for my bachelor's thesis "Abstractive Text Summarization of Meetings".
The code that uses this data can be found at 
[Abstractive Summarization of Meetings](https://github.com/Bastian/Abstractive-Summarization-of-Meetings).

## Motivation

For some meetings, there exists a link between the each dialogue act of its extractive summary and
its abstractive summary (See [AMI Corpus - Annotation](http://groups.inf.ed.ac.uk/ami/corpus/annotation.shtml), chapter 
"Abstractive and Extractive Summaries"). This Java program uses the link and concatenates all dialogue acts that
"belong" to the sentence of the abstractive summary.
Simply said, it maps `n` dialogue acts to `1` sentence.

The result may look like this:
```
// Dialogue act
the one thing for example something to eliminate maybe that's the teletext,

// Sentence of abstractive summary
They will eliminate teletext. 
```

## Data Cleaning

The program removes some words, that do not add any meaningful context.
To be precise, it takes the words from http://groups.inf.ed.ac.uk/ami/corpus/regularised_spellings.shtml
that are categorized as `BACKCHANNELS`, `HESITATIONS` and `TAG QUESTIONS`.

## Generates files

The program generates three files `data.[train|dev|test].tsv` that contain the data pairs.
It uses the split proposed by http://groups.inf.ed.ac.uk/ami/corpus/datasets.shtml by using the information
from the `meetings.xml` file.

## Dependencies

This program requires [NITE XML Toolkit](http://groups.inf.ed.ac.uk/nxt/).
The path to the toolkit is hardcoded for my PC in the [build.gradle](build.gradle) file and should be changed, if you
plan to execute the code on your own PC.

## Running the program

Just execute the code with the path to the `AMI-metadata.xml` file as the only argument.

## Parsing other datasets

The code also works with the [ICSI Meeting Corpus](http://groups.inf.ed.ac.uk/ami/icsi/).
However, as this corpus does not contain any information about how the data should be split, it will
only produce a single `data.train.tsv` that contains all the data.

Other datasets were not tested.

## License

This project is licensed under the [Apache License 2.0](/LICENSE).