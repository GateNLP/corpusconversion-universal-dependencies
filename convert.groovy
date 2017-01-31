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

gate.Gate.getUserConfig().put(gate.GateConstants.DOCEDIT_INSERT_PREPEND,true)
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

// List of strings which should get added to the document. For each string there
// is normally one token, except if there is a multi-token word.
// Entries in this list are hashes with the keys: string, tokens
wordList = []
sentenceText = ""   // current sentence text including whitespace, if known
sentenceId = ""     // current sentence id if known
// list of other comments for that sentence
sentenceComments = []

nSent = 0       // current sentence number, starting counting with 1
nLine = 0       // current line number, countring from 1
nDoc = 0        // current document number, counting from 1

// holds the current document where a sentence should get added to, or is
// null if we do not have a document yet, or if we just wrote a document.
// So, whenever this is not-null, we have something that needs eventually get
// written out.
curDoc = null

fis = new FileInputStream(inFile) 
br = new BufferedReader(new InputStreamReader(fis,"UTF-8"))
while((line = br.readLine())!= null){
  nLine += 1
  line = line.trim()
  // we simply collect all the information for each sentence and
  // once the sentence is finished (indicated by the empty line), we 
  // add the sentence and the related annotations to the current document.
  // Whenever we have finished adding the required number of sentences to 
  // a document, it gets written out.
  if(line.isEmpty()) {
    nSent += 1

    // At this point wordList should contain >1 word hashes, each of which 
    // with one or more token lists
    // Add the sentence to the document
    curDoc = addSentenceToDocument(curDoc, sentenceText, wordList, sentenceId, nSent, sentenceComments, nLine)
    curDoc = writeDocumentIfNeeded(curDoc, inFile, outDir, nsent)
    
    // reset for the next sentence
    sentenceText = ""
    wordList = []
    tokenList = []
    sentenceId = ""
    sentenceComments = []
  // if we want to use the original text, if present, remember it. If we 
  // do not want to use it, we will simply remember the comment just like other
  // comments
  } else if(useOrig && line.startsWith(sentOrigPrefix)) {
    sentenceText = line.substring(sentOrigPrefix.size())
  } else if(line.startsWith(sentIdPrefix)) {
    sentenceId = line.substring(sentIdPrefix.size())
  } else if(line.startsWith("#")) {
    sentenceComments.add(line)
  } else {
    // this should be a line that has 10 fields as described above
    tokens = line.split("\t",-1)
    if(tokens.size() != 10) {
      System.err.println("ERROR: not 10 fields in line "+nLine)
    } else {
      word = [:]
      // if we find a range, we read the expected rows in here and add the
      // tokens to the word, then add the word
      if(tokens[0].matches("[0-9]+-[0-9]+")) {        
        (from,to) = tokens[0].split("-")
        word['string'] = tokens[1]
        from = from.toInteger()
        to = to.toInteger()
        tokensForWord = []
        for(i in (from..to)) {
          line = br.readLine()
          if(line == null) {
            System.err.println("Unexpected EOF when reading multi-token word")
            System.exit(1)
          }
          line = line.trim()
          tokens = line.split("\t",-1)
          if(!tokens[0].equals(""+i)) {
            System.err.println("Token id does not match expected id in line "+nLine)
            System.exit(1)
          }
          tokensForWord.add(tokens)
        }
        word['tokens'] = tokensForWord
      } else {
        // we have got a token or empty node
        // For now we do not support empty nodes
        if(tokens[0].matches("[0-9]+\\.[0-9]+")) {
          System.err.println("Empty nodes not supported yet")
          System.exit(1)
        }
        word['string'] = tokens[1]
        word['tokens'] = [ tokens ]
      }
      wordList.add(word)
    } // we have a proper line with 10 fields
  }
}


System.err.println("INFO: number of lines read:        "+nLine)
System.err.println("INFO: number of sentences found:   "+nSent)
System.err.println("INFO: number of documents written: "+nDoc)

def addSentenceToDocument(doc, sentenceText,wordList, sentenceId, nSent, sentenceComments,nLineTo) {
  // if the doc is null, create a new one which will later returned, otherwise
  // the one we got will get returned
  if(doc == null) {
    parms = Factory.newFeatureMap()
    parms.put(Document.DOCUMENT_STRING_CONTENT_PARAMETER_NAME, "")
    parms.put(Document.DOCUMENT_MIME_TYPE_PARAMETER_NAME, "text/plain")
    doc = (Document) gate.Factory.createResource("gate.corpora.DocumentImpl", parms)
    doc.getFeatures().put("nSentFrom",nSent)
  }
  doc.getFeatures().put("nSentTo",nSent)
  outputAS = doc.getAnnotations("Key")
  curOffsetFrom = doc.getContent().size()
  // if We already have something (another sentence), then first add 
  // a new line to the document to separate the sentence we are going to add  
  if(curOffsetFrom > 0) {
    doc.edit(curOffsetFrom,curOffsetFrom,new gate.corpora.DocumentContentImpl("\n"))
    curOffsetFrom = doc.getContent().size()
  }
  curOffsetTo = curOffsetFrom
  fmDoc = doc.getFeatures()
  sb = new StringBuilder()
  token2id = [:]
  addSpace = false   // we never need to add space before the first word
  tokenInfos = []
  for(word in wordList) {
    wordString = word['string']
    // first check if we have to add whitespace after the previous token
    // we do this using the following heuristics:
    // if we have the original sentence text, then we advance until we find
    // the current word and count the whitespace. 
    // If we do not have the original sentence text, then if the addSpace flag
    // is false, we do not add a space. This is set to false in the previous
    // word if the line contained SpaceAfter=No or if the previous word was
    // one of ({[
    // Otherwise, if the current word is one of ,;:?!.)}] we do not add a space
    if(sentenceText != null && !sentenceText.isEmpty()) {
      System.err.println("Original text usage not implemented yet")
      System.exit(1)
    } else {
      if(addSpace && !wordString.matches("[,;:?!.)}\\]]")) {
        sb.append(" ")
        curOffsetFrom += 1
      }
      addSpace = true
    }    
    
    // add the word string to the document
    sb.append(wordString)
    if(wordString.matches("[({\\[]")) {
      addSpace = false
    }
    curOffsetTo = curOffsetFrom + wordString.size()
    
    
    // add the token annotations and remember how each annotation maps to the 
    // token id number
    tokens = word['tokens']
    for(token in tokens) {
      // create the token features from the field
      fm = gate.Factory.newFeatureMap()
      fm.put("string",token[1])
      fm.put("lemma",token[2])
      fm.put("upos",token[3])
      if(!token[4].equals("_")) fm.put("pos",token[4])
      tokenInfos.add([fm:fm, from:curOffsetFrom, to:curOffsetTo])
    }
    curOffsetFrom = curOffsetTo
  }
  
  // append the content string to the document
  endOffset = doc.getContent().size()
  doc.edit(endOffset,endOffset,new gate.corpora.DocumentContentImpl(sb.toString()))
  for(tokenInfo in tokenInfos) {
    gate.Utils.addAnn(outputAS,tokenInfo['from'],tokenInfo['to'],"Token",tokenInfo['fm'])
  }
  sfm = gate.Factory.newFeatureMap()
  sfm.put("gate.conversion.nSent",nSent)
  sfm.put("gate.conversion.sentId",sentenceId)
  sfm.put("gate.conversion.sentComments",sentenceComments)
  sfm.put("gate.conversion.nLineTo",nLineTo)
  sfm.put("gate.conversion.nLineFrom",(nLineTo-(tokenInfos.size())))
  gate.Utils.addAnn(outputAS,tokenInfos[0]['from'],tokenInfos[-1]['to'],"Sentence",sfm)
  return doc
}


def writeDocumentIfNeeded(doc, inFile, outDir, nsent) {
  sFrom = (int)doc.getFeatures().get("nSentFrom")
  sTo = (int)doc.getFeatures().get("nSentTo")
  if(sTo-sFrom+1 == nsent) {
    if(nsent == 1) {
      name = inFile.getName() + ".gate.s"+sFrom+".xml"
    } else {
      name = inFile.getName() + ".gate.s"+sFrom+"_"+sTo+".xml"
    }
    outFile = new File(outDir,name)
    gate.corpora.DocumentStaxUtils.writeDocument(doc,outFile)  
    System.err.println("Document saved: "+outFile)
    nDoc += 1
    gate.Factory.deleteResource(doc)
    doc = null
  }
  return doc
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