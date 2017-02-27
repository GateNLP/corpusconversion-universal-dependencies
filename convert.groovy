// conversion of universal dependencies files to GATE documents

// NOTE: this uses three different ways of trying to figure out the whitespace
// to add:
// * if -o is used it tries to use the comments, by default "sentence-text"
//   However this does often not work because the text there does not actually match
//   the word strings that follow
// If -o is not used: these two are combined
// * any SpaceAfter=No comment in the last field 
// * No space before ,:;?!')}], no space after {[(

import gate.*
import java.utils.*
import groovy.util.CliBuilder

def sentIdPrefix = "# sentid: "
def sentOrigPrefix = "# sentence-text: "


def cli = new CliBuilder(usage:'convert.groovy [-h] [-n 1] [-o] infile outdir')
cli.h(longOpt: 'help', "Show usage information")
cli.o(longOpt: 'orig', "Use original sentence text from comment line if present")
cli.n(longOpt: 'nsent', args: 1, argName: 'nsent', "Number of sentences per output document")
cli.i(longOpt: 'sentId', args: 1, argName: 'sentId', "Comment prefix for sentence id comment, without leading '# ' and trailing space.")
cli.t(longOpt: 'sentOrig', args: 1, argName: 'sentOrig', "Comment prefix for sentence text comment, without leading '# ' and trailing space.")

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
if(options.o) useOrig = true
  
if(options.i) sentIdPrefix = "# "+options.i+" "
  
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
    curDoc = writeDocumentIfNeeded(curDoc, inFile, outDir, nsent, nLine)
    
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
    tmp = tokens[1]
    if(tokens.size() != 10) {
      System.err.println("ERROR: not 10 fields in line "+nLine)
    } else {
      // There is a bug in some of the corpora where some tokens include a Unicode BOM character FEFF
      // We check each token and remove such that if necessary
      if (tmp.startsWith("\uFEFF")) {
        tmp = tmp.substring(1);
        System.err.println("DEBUG: removing FEFF from word in line nLine, word was "+tokens[1]+", is now "+tmp);
        tokens[1] = tmp;
      }
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
        // NOTE: we could get a line for an "empty" node here
        // For now we silently ignore empty nodes!
        // TODO: later, we could just add them here and then treat
        // differently when creating annotations, based on the format of the token index
        if(tokens[0].matches("[0-9]+\\.[0-9]+")) {
          // ignore: empty nodes MUST appear after actual token rows, so 
          // we will always get some token infor for a word anyway!
        } else {
          word['string'] = tokens[1]
          word['tokens'] = [ tokens ]
        }
      }
      wordList.add(word)
    } // we have a proper line with 10 fields
  }
}
// Write out any partially created document, if there is one. This does nothing
// if curDoc is null.
writeDocumentIfNeeded(curDoc, inFile, outDir, 0, nLine)


System.err.println("INFO: number of lines read:        "+nLine)
System.err.println("INFO: number of sentences found:   "+nSent)
System.err.println("INFO: number of documents written: "+nDoc)

def addSentenceToDocument(doc, sentenceText, wordList, sentenceId, nSent, sentenceComments,nLineTo) {
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
  addSpace = false   // we never need to add space before the first word
  tokenInfos = []
  // if the sentenceText is non-empty, make sure it is trimmed
  if(sentenceText != null) sentenceText = sentenceText.trim()
  stIndex = 0  // the index of where we are in sentenceText
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
      // we only get passed a non-empty sentenceText if we both found
      // a comment and the option to use it was set.
      //
      // We should only have two situations: either the current word matches
      // the current offset in the sentenceText, then we do not insert any whitespace.
      // Or the current offset points at whitespace, then we add all the whitespace
      // we find to the document. Once we hit non-whitespace, we MUST find the word
      // we expect. If this goes wrong, we log an error, reset the sentenceText
      // and instead use the heuristics-based approach
      toAdd = ""
      if(stIndex >= sentenceText.size()) {
        System.err.println("Error trying to use the original text for line "+nLineTo+" scanning beyond end")
        sentenceText = ""        
      } else {
        while(sentenceText.substring(stIndex,stIndex+1).equals(" ")) {
          toAdd += " "
          stIndex += 1
          if(stIndex > sentenceText.size()) {
            System.err.println("Error trying to use the original text for line "+nLineTo+" scanning beyond end")
            sentenceText = ""
            break
          }
        }
      }
      // if we already found a problem, just skip
      if(sentenceText.isEmpty()) {
      } else {
        if((stIndex+wordString.size())>sentenceText.size() || !sentenceText.substring(stIndex,stIndex+wordString.size()).equals(wordString)) {
          wordStrings = wordList.collect { it['string'] }
          //System.err.println("Error trying to use the original text for line "+nLineTo+" sentence "+nSent+
          //" wordString="+wordString+" found="+sentenceText.substring(stIndex,stIndex+wordString.size())+
          //" fullText="+sentenceText+" words="+wordStrings.join(","))
          System.err.println("Error trying to use the original text for line "+nLineTo+" sentence "+nSent+
          " wordString="+wordString+" found="+sentenceText.substring(stIndex,Math.min(sentenceText.size(),stIndex+wordString.size())))
          sentenceText = ""        
        } else {
          sb.append(toAdd)
        }
        stIndex += wordString.size()
      }
    } 
    // this is a separate if since in the previous one we could change sentenceText
    // to be empty so we can fall back to this method
    if(sentenceText == null || sentenceText.isEmpty()) {
      if(addSpace && !wordString.matches("[,;:?!.')}\\]]")) {
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
      fm = gate.Factory.newFeatureMap()
      fm.put("string",token[1])
      fm.put("lemma",token[2])
      fm.put("upos",token[3])
      if(!token[4].equals("_")) fm.put("category",token[4])
      if(!token[5].equals("_")) {
        keyvals = token[5].split("\\|",-1)
        for(keyval in keyvals) {
          (k,v) = keyval.split("=")
          fm.put(k,v)
        }
      }
      headId = ""
      if(!token[6].equals("_")) {
        headId = token[6]
      }
      if(!token[7].equals("_")) {
        fm.put("deprel",token[7])
      }
      if(token[8].matches(".*SpaceAfter=No.*")) {
        addSpace=false
      }
      tokenInfos.add([fm:fm, from:curOffsetFrom, to:curOffsetTo, headId:headId, idx:token[0]])
    }
    curOffsetFrom = curOffsetTo
  }
  
  // append the content string to the document
  endOffset = doc.getContent().size()
  doc.edit(endOffset,endOffset,new gate.corpora.DocumentContentImpl(sb.toString()))
  token2id = [:]
  for(tokenInfo in tokenInfos) {
    id=gate.Utils.addAnn(outputAS,tokenInfo['from'],tokenInfo['to'],"Token",tokenInfo['fm'])
    token2id[tokenInfo['idx']] = id
  }
  
  sfm = gate.Factory.newFeatureMap()
  sfm.put("gate.conversion.nSent",nSent)
  sfm.put("gate.conversion.sentId",sentenceId)
  sfm.put("gate.conversion.sentComments",sentenceComments)
  sfm.put("gate.conversion.nLineTo",nLineTo)
  sfm.put("gate.conversion.nLineFrom",(nLineTo-(tokenInfos.size())))
  sid=gate.Utils.addAnn(outputAS,tokenInfos[0]['from'],tokenInfos[-1]['to'],"Sentence",sfm)
  // now that we have all the annotation ids, go through the featuremaps again
  // and add the proper annotation id for the head and the sentence annotation id for the root
  for(tokenInfo in tokenInfos) {
    headId = tokenInfo['headId']
    if(!headId.isEmpty()) {
      if(headId.equals("0")) {
        tokenInfo['fm'].put("head",sid)
      } else {
        tokenInfo['fm'].put("head",token2id[tokenInfo['headId']])
      }
    }
  }  
  return doc
}


def writeDocumentIfNeeded(doc, inFile, outDir, nsent,nLine) {
  if(doc==null) {
    return doc
  }
  sFrom = (int)doc.getFeatures().get("nSentFrom")
  sTo = (int)doc.getFeatures().get("nSentTo")
  haveSents = sTo-sFrom+1
  if(haveSents >= nsent) {
    if(haveSents == 1) {
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
