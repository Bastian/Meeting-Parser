# AMI Meeting Parser

A simple Java program that parses the [AMI meeting corpus](http://groups.inf.ed.ac.uk/ami/corpus/).

It is used for my bachelor's thesis "Abstractive Text Summarization of Meetings".
The code that uses this data can be found at 
[Abstractive Summarization of Meetings](https://github.com/Bastian/Abstractive-Summarization-of-Meetings).

## Motivation

This program has three processing types:

### First Processing Type

For scenario meetings, there exists a link between the each dialogue act of its extractive summary and
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

### Second Processing Type

Every scenario meeting is split into multiple topics. With the second processing type, the program takes these
topics and concatenates the sentences of each topic.

The result will be a file for each meeting, that consists of multiple lines with one line for each topic:
```
Sentence 1 of 1st topic. Sentence 2 of 1st topic. ... Last Sentence of 1st topic.
...
Sentence 1 of nth topic. Sentence 2 of nth topic. ... Last Sentence of nth topic.
...
Sentence 1 of last topic. Sentence 2 of last topic. ... Last Sentence of last topic.
```

### Third Processing Type

For every meeting, an abstract summary is available. With the second processing type, the program creates
a file for every meeting with it's summary.

## Data Cleaning

The program removes some words, that do not add any meaningful context.
To be precise, it takes the words from http://groups.inf.ed.ac.uk/ami/corpus/regularised_spellings.shtml
that are categorized as `BACKCHANNELS`, `HESITATIONS` and `TAG QUESTIONS`.

## Generates files

For the first processing type, the program generates three files `data.[train|dev|test].tsv` that contain the data pairs.
It uses the split proposed by http://groups.inf.ed.ac.uk/ami/corpus/datasets.shtml by using the information
from the `meetings.xml` file.

For the second processing type, the program generates a text file for each meeting.
It has the name `topics.<meeting>.[train|dev|test].txt`.

## Dependencies

This program requires [NITE XML Toolkit](http://groups.inf.ed.ac.uk/nxt/).
The path to the toolkit is hardcoded for my PC in the [build.gradle](build.gradle) file and should be changed, if you
plan to execute the code on your own PC.

## Running the program

Just execute the code with the path to the `AMI-metadata.xml` file as the first argument and `1` or `2` as the second
argument (for the processing type).

## Parsing other datasets

The first processing type also works with the [ICSI Meeting Corpus](http://groups.inf.ed.ac.uk/ami/icsi/).
However, as this corpus does not contain any information about how the data should be split, it will
only produce a single `data.train.tsv` that contains all the data.

Other datasets were not tested.

The second processing type does not work with the ICSI Meeting Corpus.

## License

This project is licensed under the [Apache License 2.0](/LICENSE).