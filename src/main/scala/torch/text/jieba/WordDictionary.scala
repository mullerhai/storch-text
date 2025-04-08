package torch.text.jieba

import java.io.{BufferedReader, IOException, InputStreamReader}
import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.{DirectoryStream, Files, Path}
import java.util
import java.util.Locale
import scala.collection.mutable
import scala.collection.mutable.*
import scala.jdk.CollectionConverters.*
import scala.util.control.Breaks.*

object WordDictionary {
  private val MAIN_DICT = "/dict.txt"
  private val BIG_DICT = "/dict.big.txt"
  private val USER_DICT_SUFFIX = ".dict"
  private var singleton: WordDictionary = null

  def getInstance(loadNormalDict : Boolean = true): WordDictionary = {
    if (singleton == null) classOf[WordDictionary].synchronized {
      if (singleton == null) {
        singleton = new WordDictionary
        singleton.loadDict(loadNormalDict)
        println("freqs map create and load dict")
        return singleton
      }
    }
    singleton
  }
}

class WordDictionary private {
//  this.loadDict(false)
  final val loadedPath = new mutable.HashSet[String]
  final var freqs = new mutable.HashMap[String, Double]()
  private var minFreq = Double.MinValue // .MAX_VALUE
  private var total = 0.0
  private var _dict: DictSegment = new DictSegment(0.toChar)

  /**
   * for ES to initialize the user dictionary.
   *
   * @param configFile
   */
  def init(configFile: Path): Unit = {
    val abspath = configFile.toAbsolutePath.toString
    Log.debug("initialize user dictionary:" + abspath)
    classOf[WordDictionary].synchronized {
      if (loadedPath.contains(abspath)) return
      var stream: DirectoryStream[Path] = null
      try {
        stream = Files.newDirectoryStream(configFile, String.format(Locale.getDefault, "*%s", WordDictionary.USER_DICT_SUFFIX))

        for (path <- stream.asScala) {
          Log.error(String.format(Locale.getDefault, "loading dict %s", path.toString))
          WordDictionary.singleton.loadUserDict(path)
        }
        loadedPath.add(abspath)
      } catch {
        case e: IOException =>
          Log.error(String.format(Locale.getDefault, "%s: load user dict failure!", configFile.toString))
      }
    }
  }

  def loadUserDict(userDict: Path): Unit = {
    loadUserDict(userDict, StandardCharsets.UTF_8)
  }

  def loadUserDict(userDict: Path, charset: Charset): Unit = {
    try {
      val br = Files.newBufferedReader(userDict, charset)
      val s = System.currentTimeMillis
      var count = 0
      while (br.ready) {
        val line = br.readLine
        val tokens = line.split("[\t ]+")
        breakable(
          if (tokens.length < 1) break()
        )
        //        if (tokens.length < 1) {
        //          // Ignore empty line
        //          continue //todo: continue is not supported
        //        }
        var word = tokens(0)
        var freq = 3.0d
        if (tokens.length == 2) freq = tokens(1).toDouble
        word = addWord(word)
        freqs.put(word, Math.log(freq / total))
        count += 1
      }
      Log.debug(String.format(Locale.getDefault, "user dict %s load finished, tot words:%d, time elapsed:%dms", userDict.toString, count, System.currentTimeMillis - s))
      br.close()
    } catch {
      case e: IOException =>
        Log.error(String.format(Locale.getDefault, "%s: load user dict failure!", userDict.toString))
    }
  }

  def init(paths: Array[String]): Unit = {
    classOf[WordDictionary].synchronized {
      for (path <- paths) {
        if (!loadedPath.contains(path)) try {
          Log.debug("initialize user dictionary: " + path)
          WordDictionary.singleton.loadUserDict(path)
          loadedPath.add(path)
        } catch {
          case e: Exception =>
            Log.error(String.format(Locale.getDefault, "%s: load user dict failure!", path))
        }
      }
    }
  }

  def loadUserDict(userDictPath: String): Unit = {
    loadUserDict(userDictPath, StandardCharsets.UTF_8)
  }

  def loadUserDict(userDictPath: String, charset: Charset): Unit = {
    val is = this.getClass.getResourceAsStream(userDictPath)
    try {
      val br = new BufferedReader(new InputStreamReader(is, charset))
      val s = System.currentTimeMillis
      var count = 0
      while (br.ready) {
        val line = br.readLine
        val tokens = line.split("[\t ]+")
        breakable(
          if (tokens.length < 1) break()
        )
        //        if (tokens.length < 1) {
        //          // Ignore empty line
        //          continue //todo: continue is not supported
        //        }
        var word = tokens(0)
        var freq = 3.0d
        if (tokens.length == 2) freq = tokens(1).toDouble
        word = addWord(word)
        freqs.put(word, Math.log(freq / total))
        count += 1
      }
      Log.debug(String.format(Locale.getDefault, "user dict %s load finished, tot words:%d, time elapsed:%dms", userDictPath, count, System.currentTimeMillis - s))
      br.close()
    } catch {
      case e: IOException =>
        Log.error(String.format(Locale.getDefault, "%s: load user dict failure!", userDictPath))
    }
  }

  private def addWord(word: String) = if (null != word && !("" == word.trim)) {
    val key = word.trim.toLowerCase(Locale.getDefault)
    _dict.fillSegment(key.toCharArray)
    key
  }
  else null

  /**
   * let user just use their own dict instead of the default dict
   */
  def resetDict(): Unit = {
    _dict = new DictSegment(0.toChar)
    freqs.clear()
  }

  def loadDict(loadNormalDict : Boolean = true): Unit = {
    _dict = new DictSegment(0.toChar)
    val is = this.getClass.getResourceAsStream(if loadNormalDict then WordDictionary.MAIN_DICT else WordDictionary.BIG_DICT)
    try {
      val br = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")))
      val s = System.currentTimeMillis
      while (br.ready) {
        val line = br.readLine
        val tokens = line.split("[\t ]+")
        breakable(
          if (tokens.length < 2) break()
        )
        //        if (tokens.length < 2) continue //todo: continue is not supported
        var word = tokens(0)
        val freq = tokens(1).toDouble
        total += freq
        word = addWord(word)
        if freqs == null then freqs = new mutable.HashMap[String, Double]() else freqs.put(word, freq)
      }

      for ((key, value) <- freqs) {
        val newValue = math.log(value / total)
        freqs(key) = newValue
        minFreq = math.min(newValue, minFreq)
      }
      if loadNormalDict then Log.debug(String.format(Locale.getDefault, "Normal dict load finished, time elapsed %d ms", System.currentTimeMillis - s)) else Log.debug(String.format(Locale.getDefault, "Big dict load finished, time elapsed %d ms", System.currentTimeMillis - s))
    } catch {
      case e: IOException =>
        e.printStackTrace()
        Log.error(String.format(Locale.getDefault, "%s load failure!", WordDictionary.MAIN_DICT))
    } finally try if (null != is) is.close()
    catch {
      case e: IOException =>
        Log.error(String.format(Locale.getDefault, "%s close failure!", WordDictionary.MAIN_DICT))
    }
  }

  def getTrie: DictSegment = this._dict

  def getFreq(key: String): Double = if (containsWord(key)) freqs.get(key).get
  else minFreq

  def containsWord(word: String): Boolean = freqs.contains(word) //.containsKey(word)
}