# Tool to convert Universal Dependencies corpora to GATE

This is an attempt to create a script that will convert Universal dependencies corpora into GATE documents.
Most TreeBanks do not seem to have any information about document boundaries so the conversion is done by
choosing the number of sentences to put in each output GATE document, the default is one (one document per sentence).

If more than one sentence is put into a document, then each Sentence is starting after a new line character. 

The CONLL format does not include any information about white-space so a few simple heuristics are used to make
the output look reasonable. However, some treebanks contain the actual text of a sentence including whitespace
in a comment line, this can be used instead of the heuristics to create whitespace. 

## How to run

* make sure convert.sh is executable, groovy is installed and on the bin path and GATE_HOME is set
* create a directory to contain the GATE documents
* optionally: set JAVA_OPTS, if set will override the default in the script
* ./convert.sh [options] infile outdir

## Annotations and features created

