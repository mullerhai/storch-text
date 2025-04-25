package torch.text.viterbi

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.Random

class Word2Vec(
    sentences: List[List[String]],
    vectorSize: Int = 100,
    window: Int = 5,
    minCount: Int = 5,
    sg: Int = 0,
    negative: Int = 5,
    alpha: Double = 0.025,
    minAlpha: Double = 0.0001,
    iter: Int = 5
) {

  private val wordCount = mutable.Map[String, Int]()
  private val vocab = mutable.Map[String, Int]()
  private val index2word = mutable.ListBuffer[String]()
  private var syn0: Array[Array[Double]] = _
  private var syn1neg: Array[Array[Double]] = _

  private def buildVocab(): Unit = {
    for (sentence <- sentences) {
      for (word <- sentence) {
        wordCount(word) = wordCount.getOrElse(word, 0) + 1
      }
    }
    var index = 0
    for ((word, count) <- wordCount if count >= minCount) {
      vocab(word) = index
      index2word += word
      index += 1
    }
    syn0 = Array.fill(vocab.size)(Array.fill(vectorSize)(Random.nextDouble()))
    syn1neg = Array.fill(vocab.size)(Array.fill(vectorSize)(0.0))
  }

  private def trainSentenceCBOW(sentence: List[String], alpha: Double): Unit = {
    for (i <- sentence.indices) {
      val word = sentence(i)
      if (vocab.contains(word)) {
        val wordIndex = vocab(word)
        val start = Math.max(0, i - window)
        val end = Math.min(sentence.length, i + window + 1)
        val context = (start until end).filter(_ != i).map(sentence(_)).filter(vocab.contains)
        if (context.nonEmpty) {
          val l1 = Array.fill(vectorSize)(0.0)
          for (contextWord <- context) {
            val contextIndex = vocab(contextWord)
            for (j <- 0 until vectorSize) {
              l1(j) += syn0(contextIndex)(j)
            }
          }
          for (j <- 0 until vectorSize) {
            l1(j) /= context.size
          }
          val neu1e = Array.fill(vectorSize)(0.0)
          for (word <- 0 to negative) {
            val target = if (word == 0) wordIndex else sampleNegative()
            val label = if (word == 0) 1 else 0
            val f = dotProduct(l1, syn1neg(target))
            val g = (label - sigmoid(f)) * alpha
            for (j <- 0 until vectorSize) {
              neu1e(j) += g * syn1neg(target)(j)
              syn1neg(target)(j) += g * l1(j)
            }
          }
          for (contextWord <- context) {
            val contextIndex = vocab(contextWord)
            for (j <- 0 until vectorSize) {
              syn0(contextIndex)(j) += neu1e(j)
            }
          }
        }
      }
    }
  }

  private def trainSentenceSG(sentence: List[String], alpha: Double): Unit = {
    for (i <- sentence.indices) {
      val word = sentence(i)
      if (vocab.contains(word)) {
        val wordIndex = vocab(word)
        val start = Math.max(0, i - window)
        val end = Math.min(sentence.length, i + window + 1)
        val context = (start until end).filter(_ != i).map(sentence(_)).filter(vocab.contains)
        for (contextWord <- context) {
          val contextIndex = vocab(contextWord)
          val l1 = syn0(contextIndex)
          val neu1e = Array.fill(vectorSize)(0.0)
          for (word <- 0 to negative) {
            val target = if (word == 0) wordIndex else sampleNegative()
            val label = if (word == 0) 1 else 0
            val f = dotProduct(l1, syn1neg(target))
            val g = (label - sigmoid(f)) * alpha
            for (j <- 0 until vectorSize) {
              neu1e(j) += g * syn1neg(target)(j)
              syn1neg(target)(j) += g * l1(j)
            }
          }
          for (j <- 0 until vectorSize) {
            syn0(contextIndex)(j) += neu1e(j)
          }
        }
      }
    }
  }

  private def dotProduct(a: Array[Double], b: Array[Double]): Double = {
    var sum = 0.0
    for (i <- a.indices) {
      sum += a(i) * b(i)
    }
    sum
  }

  private def sigmoid(x: Double): Double = {
    1.0 / (1.0 + Math.exp(-x))
  }

  private def sampleNegative(): Int = {
    val totalCount = wordCount.values.sum
    val randomValue = Random.nextDouble() * totalCount
    var currentSum = 0
    for ((word, count) <- wordCount if vocab.contains(word)) {
      currentSum += count
      if (currentSum >= randomValue) {
        return vocab(word)
      }
    }
    vocab.head._2
  }

  def train(): Unit = {
    buildVocab()
    var currentAlpha = alpha
    val decay = (alpha - minAlpha) / iter
    for (_ <- 0 until iter) {
      for (sentence <- sentences) {
        if (sg == 0) {
          trainSentenceCBOW(sentence, currentAlpha)
        } else {
          trainSentenceSG(sentence, currentAlpha)
        }
      }
      currentAlpha -= decay
    }
  }

  def getWordVector(word: String): Option[Array[Double]] = {
    vocab.get(word).map(index => syn0(index))
  }
}