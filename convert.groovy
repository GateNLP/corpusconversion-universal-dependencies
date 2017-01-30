// conversion of universal dependencies files to GATE documents

import gate.*
import java.utils.*
import groovy.util.CliBuilder

def cli = new CliBuilder(usage:'convert.groovy [-h] [-n 1] [-o] infile outdir')
cli.h(longOpt: 'help', "Show usage information")
cli.o(longOpt: 'orig', "Use original sentence text from comment line if present")
cli.n(longOpt: 'nsent', args: 1, argName: 'nsent', "Number of sentences per output document")
cli.i(longOpt: 'sentId', args: 1, argName: 'sentId', "Comment prefix for sentence id comment")
cli.t(longOpt: 'sentOrig', args: 1, argName: 'sentOrig', "Comment prefix for sentence text comment")

def options = cli.parse(args)
if(options.h) {
  cli.usage()
  return
}

def nsent = 1
if(options.n) {
  nsent = options.n.toInteger()
}

def useOrig = false
if(options.O) useOrig = true
  
def sentIdPrefix = "# sentid: "
if(options.i) sentIdPrefix = "# "+options.i+" "
  
def sentOrigPrefix = "# sentence-text: "
if(options.t) sentOrigPrefix = "# "+options.t+" "

def posArgs = options.arguments()
if(posArgs.size() != 2) {
  cli.usage()
  System.exit(1)
}

inFile = new File(posArgs[0])
outDir = new File(posArgs[1])

if(!inFile.exists()) {
  System.err.println("ERROR: file does not exist: "+inFile.getAbsolutePath())
  System.exit(1)
}
if(!outDir.exists() || !outDir.isDirectory()) {
  System.err.println("ERROR: file does not exist or is not a directory: "+outDir.getAbsolutePath())
  System.exit(1)
}

System.err.println("INFO: input file is:        "+inFile)
System.err.println("INFO: output dir is:        "+outDir)
System.err.println("INFO: sentences per doc:    "+nsent)
System.err.println("INFO: use original text:    "+useOrig)
System.err.println("INFO: sentence id prefix:   >>"+sentIdPrefix+"<<")
System.err.println("INFO: sentence text prefix: >>"+sentOrigPrefix+"<<")

gate.Gate.init()

sentences = []  // current list of sentences to convert to a document
tokenList = []  // current list of tokens for the current sentence
sentenceTexts = [] // current list of sentence texts including whitespace, if known
sentenceText = ""   // current sentence text including whitespace, if known
sentenceId = ""
sentenceIds = []

nSent = 0       // current sentence number, starting counting with 1
nLine = 0
nDoc = 0

// Columns in the CONLL format: see http://universaldependencies.org/format.html
// 0: token number, starting at 1. "may be a range for multiword tokens, may be a decimal number for empty nodes"
// 1: token string: word form or punctuation
// 2: lemma or stem
// 3: universal dependency POS tag
// 4: original POS tag or _ if not included
// 5: morpholoical features in the form Key1=Value1|Key2=Value2 or _ if empty
// 6: index of the head of the current word which is either a token number or 0
// 7: universial dependency relation to the head or a defined language-specific subtype of one
// 8: Enhanced dependency graph in the form of a list of head-deprel pairs
// 9: other annotations, sometimes contains SpaceAfter=No
//
// token ranges: have a token number of the form n-m and only contain the token string, everything else is empty/underscore
//   this is then followed by the parts, e.g.
//   1-2 vamonos _
//   1   vamos ir
//   2   nos   nosotros
// We convert this to a single token "vamonos" with feature lemma="ir nosotros"
//
// Empty nodes are indicated by a token number of the form n.m 
inFile.eachLine { line ->
  nLine += 1
  line = line.trim()
  // we simply collect all the token information for each sentence in here and
  // put each sentence into a list as soon as we have it. Once we have 
  // the required number of sentences we create a document from that using
  // writeDocument(sentences,sentenceTexts,sentenceIds,nSent,inFile,outDir)
  if(line.isEmpty()) {
    // end of sentence, add the current token list to the sentences
    nSent += 1
    sentences.add(tokenList)
    sentenceTexts.add(sentenceText)
    sentenceText = ""
    tokenList = []
    sentenceIds.add(sentenceId)
    sentenceId = ""
    if(sentences.size() == nsent) {
      writeDocument(sentences,sentenceTexts,sentenceIds,nSent,inFile,outDir)
      sentences = []
      sentenceTexts = []
      sentenceIds = []
    }
  } else if(line.startsWith(sentOrigPrefix)) {
    sentenceText = line.substring(sentOrigPrefix.size())
  } else if(line.startsWith(sentIdPrefix)) {
    sentenceId = line.substring(sentIdPrefix.size())
  } else if(line.startsWith("#")) {
    // some other comment line to remember for this sentence
  } else {
    token = line.split("\t",-1)
    if(token.size() != 10) {
      System.err.println("ERROR: not 10 fields in line "+nLine)
    } else {
      // we have to distinguish two cases: normal tokens and multitoken-words
    }
  }
}


System.err.println("INFO: number of lines read:        "+nLine)
System.err.println("INFO: number of sentences found:   "+nSent)
System.err.println("INFO: number of documents written: "+nDoc)

def writeDocument(sentences, sentenceTexts,sentenceIds,nSent,inFile,outDir) {
  // NOTE: sentenceIds not used yet, just a placeholder for later!
  content = sentences.join("\n")
  //System.err.println("Got content: "+content.size())
  parms = Factory.newFeatureMap()
  parms.put(Document.DOCUMENT_STRING_CONTENT_PARAMETER_NAME, content)
  parms.put(Document.DOCUMENT_MIME_TYPE_PARAMETER_NAME, "text/plain")
  doc = (Document) gate.Factory.createResource("gate.corpora.DocumentImpl", parms)
  fmDoc = doc.getFeatures()
  //for(int i=0; i<fs.size(); i++) {
  //  gate.Utils.addAnn(doc.getAnnotations("Key"),froms.get(i),tos.get(i),"Token",fs.get(i));
  //}
  //System.err.println("DEBUG: featurs added, writing")
  sFrom = nSent - sentences.size() + 1
  sTo = nSent
  name = inFile.getName() + ".gate.s"+sFrom+"_"+sTo+".xml"
  outFile = new File(outDir,name)
  gate.corpora.DocumentStaxUtils.writeDocument(doc,outFile)
  nDoc += 1
  //System.err.println("Document saved: "+outFile)
}

/*

snr = 0
StringBuilder sb = new StringBuilder()
ArrayList<FeatureMap> fs = new ArrayList<FeatureMap>()
ArrayList<Integer> froms = new ArrayList<Integer>()
ArrayList<Integer> tos = new ArrayList<Integer>()
curFrom = 0
curTo = 0
sidFrom = ""
body.s.each { sentence -> 
  //System.println("Processing sentence " + sentence.attributes()["id"])
  // we count sentences: whenever we got SMAX, we save what we have to
  // a new document
  snr += 1
  if(sidFrom.isEmpty()) {
    sidFrom = sentence.attributes()["id"]
  }
  sidTo = sentence.attributes()["id"]
  // get the list of terminals
  terms = sentence.graph.terminals.t
  terms.each { term -> 
    a = term.attributes()
    string = a["word"]
    fm = gate.Utils.featureMap(
      "lemma",a["lemma"],
      "pos",a["pos"],
      "morph",a["morph"],
      "case",a["case"],
      "number",a["number"],
      "gender",a["gender"],
      "person",a["person"],
      "degree",a["degree"],
      "tense",a["tense"],
      "mood",a["mood"]
    )
    // add the string to the sb and remember the start and end offset of 
    // the annotation
    if(string.equals(",") || string.equals(";") || string.equals("!") || string.equals("?") || string.equals(".") ||
       string.equals(":") || string.equals(")")) {
      sb.append(string)
      curTo += string.size()
    } else {
      sb.append(" ")
      sb.append(string)
      curFrom += 1
      curTo = curTo + string.size() +1
    }
    fs.add(fm)
    froms.add(curFrom)
    tos.add(curTo)
    curFrom = curTo
  }
  if(snr == SMAX) {
    // save what we have to a gate document
    name = "tiger_" + sidFrom + "_" + sidTo + ".xml"
    writeDocument(sb,fs,froms,tos, name)
    // reset 
    snr = 0
    curFrom = 0
    curTo = 0
    sidFrom = ""
    sb = new StringBuilder()
    fs = new ArrayList<FeatureMap>()
    froms = new ArrayList<Integer>()
    tos = new ArrayList<Integer>()
  }
  
}

if(sb.length() > 0) {
  name = "tiger_" + sidFrom + "_" + sidTo + ".xml"
  writeDocument(sb,fs,froms,tos,name)
}

*/