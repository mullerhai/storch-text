package torch.text.jieba

import torch.text.viterbi.FinalSeg

import java.nio.file.Path
import scala.collection.mutable
import scala.collection.mutable.*

object JiebaSegmenter {
  private val wordDict = WordDictionary.getInstance(true)
  private val finalSeg = FinalSeg.getInstance

  enum SegMode:
    case INDEX, SEARCH

}

class JiebaSegmenter {
  /**
   * initialize the user dictionary.
   *
   * @param path user dict dir
   */
  def initUserDict(path: Path): Unit = {
    JiebaSegmenter.wordDict.init(path)
  }

  def initUserDict(paths: Array[String]): Unit = {
    JiebaSegmenter.wordDict.init(paths)
  }

  def process(paragraph: String, mode: JiebaSegmenter.SegMode): ListBuffer[SegToken] = {
    val tokens = new ListBuffer[SegToken]
    var sb = new StringBuilder
    var offset = 0
    for (i <- 0 until paragraph.length) {
      val ch = CharacterUtil.regularize(paragraph.charAt(i))
      if (CharacterUtil.ccFind(ch)) sb.append(ch)
      else {
        if (sb.length > 0) {
          // process
          if (mode eq JiebaSegmenter.SegMode.SEARCH) {
            //            import scala.collection.JavaConversions.*
            for (word <- sentenceProcess(sb.toString)) {
              val tmpOldOffset = offset
              offset += word.length
              tokens.append(new SegToken(word, tmpOldOffset, offset))
            }
          }
          else {
            //            import scala.collection.JavaConversions.*
            for (token <- sentenceProcess(sb.toString)) {
              if (token.length > 2) {
                var gram2: String = null
                var j = 0
                while (j < token.length - 1) {
                  gram2 = token.substring(j, j + 2)
                  if (JiebaSegmenter.wordDict.containsWord(gram2)) tokens.append(new SegToken(gram2, offset + j, offset + j + 2))
                  j += 1
                }
              }
              if (token.length > 3) {
                var gram3: String = null
                var j = 0
                while (j < token.length - 2) {
                  gram3 = token.substring(j, j + 3)
                  if (JiebaSegmenter.wordDict.containsWord(gram3)) tokens.append(new SegToken(gram3, offset + j, offset + j + 3))
                  j += 1
                }
              }
              val tmpOldOffset = offset
              offset += token.length
              tokens.append(new SegToken(token, tmpOldOffset, offset))
            }
          }
          sb = new StringBuilder
          offset = i
        }
        if (JiebaSegmenter.wordDict.containsWord(paragraph.substring(i, i + 1))) tokens.append(new SegToken(paragraph.substring(i, i + 1), offset, {
          offset += 1;
          offset
        }))
        else tokens.append(new SegToken(paragraph.substring(i, i + 1), offset, {
          offset += 1;
          offset
        }))
      }
    }
    if (sb.length > 0) if (mode eq JiebaSegmenter.SegMode.SEARCH) {
      //      import scala.collection.JavaConversions.*
      for (token <- sentenceProcess(sb.toString)) {
        val tmpOldOffset = offset
        offset += token.length
        tokens.append(new SegToken(token, tmpOldOffset, offset))
      }
    }
    else {
      //      import scala.collection.JavaConversions.*
      for (token <- sentenceProcess(sb.toString)) {
        if (token.length > 2) {
          var gram2: String = null
          var j = 0
          while (j < token.length - 1) {
            gram2 = token.substring(j, j + 2)
            if (JiebaSegmenter.wordDict.containsWord(gram2)) tokens.append(new SegToken(gram2, offset + j, offset + j + 2))
            j += 1
          }
        }
        if (token.length > 3) {
          var gram3: String = null
          var j = 0
          while (j < token.length - 2) {
            gram3 = token.substring(j, j + 3)
            if (JiebaSegmenter.wordDict.containsWord(gram3)) tokens.append(new SegToken(gram3, offset + j, offset + j + 3))
            j += 1
          }
        }
        val tmpOldOffset = offset
        offset += token.length
        tokens.append(new SegToken(token, tmpOldOffset, offset))
      }
    }
    tokens
  }

  /*
       *
       */
  def sentenceProcess(sentence: String): ListBuffer[String] = {
    val tokens = new ListBuffer[String]
    val N = sentence.length
    val dag = createDAG(sentence)
    val route = calc(sentence, dag)
    var x = 0
    var y = 0
    var buf: String = null
    var sb = new StringBuilder
    while (x < N) {
      y = route.get(x).get.key + 1
      val lWord = sentence.substring(x, y)
      if (y - x == 1) sb.append(lWord)
      else {
        if (sb.length > 0) {
          buf = sb.toString
          sb = new StringBuilder
          if (buf.length == 1) tokens.append(buf)
          else if (JiebaSegmenter.wordDict.containsWord(buf)) tokens.append(buf)
          else JiebaSegmenter.finalSeg.cut(buf, tokens)
        }
        tokens.append(lWord)
      }
      x = y
    }
    buf = sb.toString
    if (buf.length > 0) if (buf.length == 1) tokens.append(buf)
    else if (JiebaSegmenter.wordDict.containsWord(buf)) tokens.append(buf)
    else JiebaSegmenter.finalSeg.cut(buf, tokens)
    tokens
  }

  private def createDAG(sentence: String) = {
    val dag = new mutable.HashMap[Integer, ListBuffer[Integer]]
    val trie = JiebaSegmenter.wordDict.getTrie
    val chars = sentence.toCharArray
    val N = chars.length
    var i = 0
    var j = 0
    while (i < N) {
      val hit = trie.matchChars(chars, i, j - i + 1)
      if (hit.isPrefix || hit.isMatch) {
        if (hit.isMatch) if (!dag.contains(i)) {
          val value = new ListBuffer[Integer]
          dag.put(i, value)
          value.append(j)
        }
        else dag.get(i).get.append(j)
        j += 1
        if (j >= N) {
          i += 1
          j = i
        }
      }
      else {
        i += 1
        j = i
      }
    }
    i = 0
    while (i < N) {
      if (!dag.contains(i)) {
        val value = new ListBuffer[Integer]
        value.append(i)
        dag.put(i, value)
      }
      i += 1
    }
    dag
  }

  private def calc(sentence: String, dag: mutable.HashMap[Integer, ListBuffer[Integer]]) = {
    val N = sentence.length
    val route = new mutable.HashMap[Integer, Pair[Integer]]
    route.put(N, new Pair[Integer](0, 0.0))
    for (i <- N - 1 until -1 by -1) {
      var candidate: Pair[Integer] = null
      //      import scala.collection.JavaConversions.*
      for (x <- dag.get(i).get) {
        val freq = JiebaSegmenter.wordDict.getFreq(sentence.substring(i, x + 1)) + route.get(x + 1).get.freq
        if (null == candidate) candidate = new Pair[Integer](x, freq)
        else if (candidate.freq < freq) {
          candidate.freq = freq
          candidate.key = x
        }
      }
      route.put(i, candidate)
    }
    route
  }
}


//  object SegMode extends Enumeration {
//    type SegMode = Value
//    val INDEX, SEARCH = Value
//  }