package torch.text.viterbi

import torch.text.jieba.{CharacterUtil, Log, Node, Pair}

import java.io.{BufferedReader, IOException, InputStreamReader}
import java.nio.charset.Charset
import java.util
import java.util.{Collections, Locale}
import scala.collection.mutable
import scala.collection.mutable.{HashMap, ListBuffer}

object FinalSeg {
  private val PROB_EMIT = "/prob_emit.txt"
  private val states = Array[Char]('B', 'M', 'E', 'S')
  private val MIN_FLOAT = -3.14e100
  private var singleInstance: FinalSeg = null
  private var emit: HashMap[Character, HashMap[Character, Double]] = null
  private var start: HashMap[Character, Double] = null
  private var trans: HashMap[Character, mutable.HashMap[Character, Double]] = null
  private var prevStatus: mutable.HashMap[Character, Array[Char]] = null

  def getInstance: FinalSeg = {
    if (null == singleInstance) {
      singleInstance = new FinalSeg
      singleInstance.loadModel()
    }
    singleInstance
  }
}

class FinalSeg private {
  def cut(sentence: String, tokens: ListBuffer[String]): Unit = {
    var chinese = new StringBuilder
    var other = new StringBuilder
    for (i <- 0 until sentence.length) {
      val ch = sentence.charAt(i)
      if (CharacterUtil.isChineseLetter(ch)) {
        if (other.length > 0) {
          processOtherUnknownWords(other.toString, tokens)
          other = new StringBuilder
        }
        chinese.append(ch)
      }
      else {
        if (chinese.length > 0) {
          viterbi(chinese.toString, tokens)
          chinese = new StringBuilder
        }
        other.append(ch)
      }
    }
    if (chinese.length > 0) viterbi(chinese.toString, tokens)
    else processOtherUnknownWords(other.toString, tokens)
  }

  def viterbi(sentence: String, tokens: ListBuffer[String]): Unit = {
    val v = new util.Vector[mutable.HashMap[Character, Double]]
    var path = new mutable.HashMap[Character, Node]
    v.add(new mutable.HashMap[Character, Double])
    for (state <- FinalSeg.states) {
      var emP: Double = FinalSeg.emit.get(state).get(sentence.charAt(0))
      if (emP.isNaN) emP = FinalSeg.MIN_FLOAT
      v.get(0).put(state, FinalSeg.start.get(state).get + emP)
      path.put(state, new Node(state, null))
    }
    for (i <- 1 until sentence.length) {
      val vv = new mutable.HashMap[Character, Double]
      v.add(vv)
      val newPath = new mutable.HashMap[Character, Node]
      for (y <- FinalSeg.states) {
        var emp: Double = FinalSeg.emit.get(y).get(sentence.charAt(i))
        if (emp.isNaN) emp = FinalSeg.MIN_FLOAT
        var candidate: Pair[Character] = null
        for (y0 <- FinalSeg.prevStatus.get(y).get) {
          var tranp = FinalSeg.trans.get(y0).get(y)
          if (tranp.isNaN) tranp = FinalSeg.MIN_FLOAT
          tranp += (emp + v.get(i - 1).get(y0).get)
          if (null == candidate) candidate = new Pair[Character](y0, tranp)
          else if (candidate.freq <= tranp) {
            candidate.freq = tranp
            candidate.key = y0
          }
        }
        vv.put(y, candidate.freq)
        newPath.put(y, new Node(y, path.get(candidate.key).get))
      }
      path = newPath
    }
    val probE = v.get(sentence.length - 1).get('E').get
    val probS = v.get(sentence.length - 1).get('S').get
    val posList = new util.Vector[Character](sentence.length)
    var win: Node = null
    if (probE < probS) win = path('S')
    else win = path('E')
    while (win != null) {
      posList.add(win.value)
      win = win.parent
    }
    Collections.reverse(posList)
    var begin = 0
    var next = 0
    for (i <- 0 until sentence.length) {
      val pos = posList.get(i)
      if (pos == 'B') begin = i
      else if (pos == 'E') {
        tokens.append(sentence.substring(begin, i + 1))
        next = i + 1
      }
      else if (pos == 'S') {
        tokens.append(sentence.substring(i, i + 1))
        next = i + 1
      }
    }
    if (next < sentence.length) tokens.append(sentence.substring(next))
  }

  private def processOtherUnknownWords(other: String, tokens: ListBuffer[String]): Unit = {
    val mat = CharacterUtil.reSkip.matcher(other)
    var offset = 0
    while (mat.find) {
      if (mat.start > offset) tokens.append(other.substring(offset, mat.start))
      tokens.append(mat.group)
      offset = mat.end
    }
    if (offset < other.length) tokens.append(other.substring(offset))
  }

  private def loadModel(): Unit = {
    val s = System.currentTimeMillis
    FinalSeg.prevStatus = new mutable.HashMap[Character, Array[Char]]
    FinalSeg.prevStatus.put('B', Array[Char]('E', 'S'))
    FinalSeg.prevStatus.put('M', Array[Char]('M', 'B'))
    FinalSeg.prevStatus.put('S', Array[Char]('S', 'E'))
    FinalSeg.prevStatus.put('E', Array[Char]('B', 'M'))
    FinalSeg.start = new mutable.HashMap[Character, Double]
    FinalSeg.start.put('B', -0.26268660809250016)
    FinalSeg.start.put('E', -3.14e+100)
    FinalSeg.start.put('M', -3.14e+100)
    FinalSeg.start.put('S', -1.4652633398537678)
    FinalSeg.trans = new mutable.HashMap[Character, mutable.HashMap[Character, Double]]
    val transB = new mutable.HashMap[Character, Double]
    transB.put('E', -0.510825623765990)
    transB.put('M', -0.916290731874155)
    FinalSeg.trans.put('B', transB)
    val transE = new mutable.HashMap[Character, Double]
    transE.put('B', -0.5897149736854513)
    transE.put('S', -0.8085250474669937)
    FinalSeg.trans.put('E', transE)
    val transM = new mutable.HashMap[Character, Double]
    transM.put('E', -0.33344856811948514)
    transM.put('M', -1.2603623820268226)
    FinalSeg.trans.put('M', transM)
    val transS = new mutable.HashMap[Character, Double]
    transS.put('B', -0.7211965654669841)
    transS.put('S', -0.6658631448798212)
    FinalSeg.trans.put('S', transS)
    val is = this.getClass.getResourceAsStream(FinalSeg.PROB_EMIT)
    try {
      val br = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")))
      FinalSeg.emit = new mutable.HashMap[Character, mutable.HashMap[Character, Double]]
      var values: mutable.HashMap[Character, Double] = null
      while (br.ready) {
        val line = br.readLine
        val tokens = line.split("\t")
        if (tokens.length == 1) {
          values = new mutable.HashMap[Character, Double]
          FinalSeg.emit.put(tokens(0).charAt(0), values)
        }
        else values.put(tokens(0).charAt(0), tokens(1).toDouble)
      }
    } catch {
      case e: IOException =>
        Log.error(String.format(Locale.getDefault, "%s: load model failure!", FinalSeg.PROB_EMIT))
    } finally try if (null != is) is.close()
    catch {
      case e: IOException =>
        Log.error(String.format(Locale.getDefault, "%s: close failure!", FinalSeg.PROB_EMIT))
    }
    Log.debug(String.format(Locale.getDefault, "model load finished, time elapsed %d ms.", System.currentTimeMillis - s))
  }
}