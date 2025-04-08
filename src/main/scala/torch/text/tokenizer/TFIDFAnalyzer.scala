package torch.text.tokenizer

import torch.text.jieba.{JiebaSegmenter, Keyword}
import torch.text.tokenizer.TFIDFAnalyzer

import java.io.*
import scala.collection.mutable
import scala.collection.mutable.*
import scala.io.Source
import scala.util.{Try, Using}


/**
 * @author muller
 * @email
 * @github
 * @date 2025/4/8
 *       tfidf算法原理参考：http://www.cnblogs.com/ywl925/p/3275878.html
 *       部分实现思路参考jieba分词：https://github.com/fxsjy/jieba
 */
object TFIDFAnalyzer {
  var idfMap: mutable.HashMap[String, Double] = new mutable.HashMap[String, Double]()
  var stopWordsSet: mutable.HashSet[String] = new mutable.HashSet[String]()
  var idfMedian = .0
}

class TFIDFAnalyzer {
  /**
   * tfidf分析方法
   *
   * @param content 需要分析的文本/文档内容
   * @param topN    需要返回的tfidf值最高的N个关键词，若超过content本身含有的词语上限数目，则默认返回全部
   * @return
   */
  def analyze(content: String, topN: Int): ListBuffer[Keyword] = {
    var keywordList = new ListBuffer[Keyword]
    if (TFIDFAnalyzer.stopWordsSet.isEmpty) {
      TFIDFAnalyzer.stopWordsSet = new mutable.HashSet[String]
      loadStopWords(TFIDFAnalyzer.stopWordsSet, this.getClass.getResourceAsStream("/stop_words.txt"))
    }
    if (TFIDFAnalyzer.idfMap.isEmpty) {
      TFIDFAnalyzer.idfMap = new mutable.HashMap[String, Double]
      loadIDFMap(TFIDFAnalyzer.idfMap, this.getClass.getResourceAsStream("/idf_dict.txt"))
    }
    val tfMap = getTF(content)
    for (word <- tfMap.keySet) {
      // 若该词不在idf文档中，则使用平均的idf值(可能定期需要对新出现的网络词语进行纳入)
      if (TFIDFAnalyzer.idfMap.contains(word)) keywordList.append(new Keyword(word, TFIDFAnalyzer.idfMap.get(word).get * tfMap.get(word).get))
      else keywordList.append(new Keyword(word, TFIDFAnalyzer.idfMedian * tfMap.get(word).get))
    }
    keywordList = keywordList.sorted
    //      Collections.sort(keywordList)
    if (keywordList.size > topN) {
      val num = keywordList.size - topN
      for (i <- 0 until num) {
        keywordList.remove(topN)
      }
    }
    keywordList
  }

  /**
   * tf值计算公式
   * tf=N(i,j)/(sum(N(k,j) for all k))
   * N(i,j)表示词语Ni在该文档d（content）中出现的频率，sum(N(k,j))代表所有词语在文档d中出现的频率之和
   *
   * @param content
   * @return
   */
  private def getTF(content: String): mutable.HashMap[String, Double] = {
    val tfMap = new mutable.HashMap[String, Double]
    if (content == null || content == "") return tfMap
    val segmenter = new JiebaSegmenter
    val segments = segmenter.sentenceProcess(content)
    val freqMap = new mutable.HashMap[String, Integer]
    var wordSum = 0

    for (segment <- segments) {
      //停用词不予考虑，单字词不予考虑
      if (!TFIDFAnalyzer.stopWordsSet.contains(segment) && segment.length > 1) {
        wordSum += 1
        if (freqMap.contains(segment)) freqMap.put(segment, freqMap.get(segment).get + 1)
        else freqMap.put(segment, 1)
      }
    }
    // 计算double型的tf值
    //    import scala.collection.JavaConversions.*
    for (word <- freqMap.keySet) {
      tfMap.put(word, freqMap.get(word).get * 0.1 / wordSum)
    }
    tfMap
  }

  /**
   * 默认jieba分词的停词表
   * url:https://github.com/yanyiwu/nodejieba/blob/master/dict/stop_words.utf8
   *
   * @param set
   * @param filePath
   */


  private def loadStopWords(set: mutable.Set[String], in: InputStream): Unit = {
    // 使用 Scala 2.13+ 的 Using 管理资源，自动关闭流
    Using(Source.fromInputStream(in)) { source =>
      // 逐行读取、trim 并添加到集合
      source.getLines()
        .map(_.trim)
        .foreach(set.add)
    }.recover { // 统一处理所有异常
      case e: Exception => e.printStackTrace()
    }
  }


  /**
   * idf值本来需要语料库来自己按照公式进行计算，不过jieba分词已经提供了一份很好的idf字典，所以默认直接使用jieba分词的idf字典
   * url:https://raw.githubusercontent.com/yanyiwu/nodejieba/master/dict/idf.utf8
   *
   * @param set
   * @param filePath
   */

  def loadIDFMap(map: mutable.Map[String, Double], in: InputStream): Unit = {
    Try {
      Using.resource(new BufferedReader(new InputStreamReader(in))) { reader =>
        reader.lines().forEach { line =>
          line.trim.split("\\s+") match {
            case Array(k, v) => map += (k -> v.toDouble)
            case _ => // 忽略无效行
          }
        }
      }

      val sortedValues = map.values.toVector.sorted
      TFIDFAnalyzer.idfMedian = sortedValues(sortedValues.length / 2)
    }.recover { case e => e.printStackTrace() }
  }

}
//  private def loadIDFMaps(map: mutable.Map[String, Double], in: InputStream): Unit = {
//    // 第一部分：读取数据到 Map 并计算中位数
//    val idfMedian = Using.Manager { use =>
//      val source = use(Source.fromInputStream(in))
//      source.getLines()
//        .map(_.trim.split(" "))     // 切割每行 key-value
//        .collect { case Array(k, v) => k -> v.toDouble }  // 安全解析
//        .foreach { case (k, v) => map.put(k, v) }         // 填充 Map
//      // 第二部分：计算中位数（直接在资源块内操作）
//      val idfList = map.values.toArray.sorted
//      TFIDFAnalyzer.idfMedian = idfList(idfList.length / 2)  // 原逻辑直接取中间位置
//    }.getOrElse {
//      // 默认值（根据需求可调整）
//      Double.NaN
//    }
//    // 将中位数保存到类成员变量（根据原 Java 代码语义）
////    TFIDFAnalyzer.idfMedian
//  }.recover { case e: Exception =>
//    e.printStackTrace()
//  }.get

//  private def loadIDFMap(map: mutable.HashMap[String, Double], in: InputStream): Unit = {
//    var bufr: BufferedReader = null
//    try {
//      bufr = new BufferedReader(new InputStreamReader(in))
//      var line: String = null
//      while ((line = bufr.readLine) != null) {
//        val kv = line.trim.split(" ")
//        map.put(kv(0), kv(1).toDouble)
//      }
//      try bufr.close()
//      catch {
//        case e: IOException =>
//          e.printStackTrace()
//      }
//      // 计算idf值的中位数
//      val idfList = new ArrayBuffer[Double](map.values)
//      Collections.sort(idfList)
//      TFIDFAnalyzer.idfMedian = idfList.get(idfList.size / 2)
//    } catch {
//      case e: Exception =>
//        e.printStackTrace()
//    }
//  }

//  private def loadStopWordss(set: mutable.HashSet[String], in: InputStream): Unit = {
//    var bufr: BufferedReader = null
//    try {
//      bufr = new BufferedReader(new InputStreamReader(in))
//      var line: String = null
//      while ((line = bufr.readLine) != null) set.add(line.trim)
//      try bufr.close()
//      catch {
//        case e: IOException =>
//          e.printStackTrace()
//      }
//    } catch {
//      case e: Exception =>
//        e.printStackTrace()
//    }
//  }