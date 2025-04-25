//package torch.text.tokenizer
//
//import com.google.api.client.util.{Maps, Sets}
//import com.google.common.primitives.Chars
//import org.apache.commons.lang3.tuple.MutablePair
//import org.apache.tomcat.util.json.{JSONParser, ParseException}
//import org.springframework.core.io.ClassPathResource
//
//import java.io.{IOException, InputStreamReader}
//import java.math.BigInteger
//import java.nio.ByteBuffer
//import java.nio.charset.StandardCharsets
//import java.nio.file.{Files, Paths}
//import java.util
//import java.util.regex.{Matcher, Pattern}
//import java.util.stream.{Collectors, IntStream, Stream}
//
//object GPT2Tokenizer {
//  def fromPretrained(path: String) = new GPT2Tokenizer(path)
//}
//
//object Constants {
//  val ENCODER_FILE_NAME = "encoder.json"
//  val VOCAB_FILE_NAME = "vocab.bpe"
//}
//class GPT22Tokenizer private(path: String) extends Tokenizer {
//  val encoderFile = new Nothing(Paths.get(path, Constants.ENCODER_FILE_NAME).toString)
//  val bpeFile = new Nothing(Paths.get(path, Constants.VOCAB_FILE_NAME).toString)
//  try {
//    this.encoder = new Nothing(new InputStreamReader(encoderFile.getInputStream, StandardCharsets.UTF_8)).parseObject
//    this.decoder = encoder.entrySet.stream.collect(Collectors.toMap(util.Map.Entry.getValue, util.Map.Entry.getKey))
//    this.bpe = Files.readAllLines(Paths.get(bpeFile.getURI), StandardCharsets.UTF_8)
//    for (i <- 0 until this.bpe.size) {
//      val pairs = bpe.get(i).split(" ")
//      this.bpeRanks.put(MutablePair.of(pairs(0), pairs(1)), i)
//    }
//  } catch {
//    case e@(_: IOException | _: Nothing) =>
//      e.printStackTrace
//  }
//  private var bpe: util.List[String] = null
//  private var encoder: util.Map[String, AnyRef] = null
//  private var decoder: util.Map[AnyRef, String] = null
//  private val cache = Maps.newHashMap
//  private val byte2unicode = byteToUnicode
//  private val bpeRanks = Maps.newHashMap
//  private val pattern = Pattern.compile("'s|'t|'re|'ve|'m|'ll|'d| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+")
//
//  private def getPairs(word: util.List[String]) = {
//    val pairs = Sets.newHashSet
//    var prevCharacter = word.get(0)
//    import scala.collection.JavaConversions.*
//    for (character <- word.subList(1, word.size)) {
//      pairs.add(new MutablePair[String, String](prevCharacter, character))
//      prevCharacter = character
//    }
//    pairs
//  }
//
//  private def byteToUnicode = {
//    val bs = Stream.of(IntStream.range('!', '~' + 1).boxed, IntStream.range('¡', '¬' + 1).boxed, IntStream.range('®', 'ÿ' + 1).boxed).reduce(Stream.concat).get.collect(Collectors.toList)
//    val cs = new util.ArrayList[Integer](util.List.copyOf(bs))
//    var n = 0
//    val max = Math.pow(2, 8).toInt
//    for (b <- 0 until max) {
//      if (!bs.contains(b)) {
//        bs.add(b)
//        cs.add(max + n)
//        n += 1
//      }
//    }
//    val csString = cs.stream.map((i: Integer) => String.valueOf(Character.toChars(i))).collect(Collectors.toList)
//    val output = Maps.newHashMap
//    for (i <- 0 until bs.size) {
//      output.put(bs.get(i), csString.get(i))
//    }
//    output
//  }
//
//  private def bpe(token: String): String = {
//    if (cache.containsKey(token)) return cache.get(token)
//    var word = token.chars.mapToObj((i: Int) => String.valueOf(i.toChar)).collect(Collectors.toList)
//    var pairs = getPairs(word)
//    while (true) {
//      var minScore = Integer.MAX_VALUE
//      var biGram: MutablePair[String, String] = null
//      import scala.collection.JavaConversions.*
//      for (pair <- pairs) {
//        if (bpeRanks.containsKey(pair)) {
//          val score = bpeRanks.get(pair)
//          if (score < minScore) {
//            minScore = score
//            biGram = pair
//          }
//        }
//      }
//      if (biGram == null) break //todo: break is not supported
//      val first = biGram.left
//      val second = biGram.right
//      val newWord = new util.ArrayList[String]
//      var i = 0
//      while (i < word.size) {
//        val j = indexWithStartPosition(word, first, i)
//        if (j != -1) {
//          newWord.addAll(word.subList(i, j))
//          i = j
//        }
//        else {
//          newWord.addAll(word.subList(i, word.size))
//          break //todo: break is not supported
//        }
//        if (word.get(i) == first && i < word.size - 1 && word.get(i + 1) == second) {
//          newWord.add(first + second)
//          i += 2
//        }
//        else {
//          newWord.add(word.get(i))
//          i += 1
//        }
//      }
//      word = newWord
//      if (word.size == 1) break //todo: break is not supported
//      else pairs = getPairs(word)
//    }
//    val output = String.join(" ", word)
//    cache.put(token, output)
//    output
//  }
//
//  private def indexWithStartPosition[T](list: util.List[T], find: T, startPosition: Int): Int = {
//    if (list == null || list.isEmpty) return -1
//    for (index <- startPosition until list.size) {
//      if (list.get(index) == find) return index
//    }
//      - 1
//  }
//
//  def encode(text: String): util.List[Integer] = {
//    val matcher = pattern.matcher(text)
//    val unicodes = new util.ArrayList[String]
//    val bpeTokens = new util.ArrayList[Integer]
//    while (matcher.find) {
//      val `match` = matcher.group
//      val unicodeBuilder = new lang.StringBuilder
//      for (b <- `match`.getBytes(StandardCharsets.UTF_8)) {
//        unicodeBuilder.append(this.byte2unicode.get(b.toInt))
//      }
//      unicodes.add(unicodeBuilder.toString)
//    }
//    import scala.collection.JavaConversions.*
//    for (token <- unicodes) {
//      for (bpeToken <- bpe(token).split(" ")) {
//        bpeTokens.add(encoder.get(bpeToken).asInstanceOf[BigInteger].intValue)
//      }
//    }
//    bpeTokens
//  }
//
//  def decode(tokens: util.List[Integer]): String = {
//    val textBuilder = new lang.StringBuilder
//    val byteBufferList = new util.ArrayList[String]
//    import scala.collection.JavaConversions.*
//    for (token <- tokens) {
//      textBuilder.append(decoder.get(BigInteger.valueOf(token)))
//    }
//    val text = textBuilder.toString
//    for (i <- 0 until text.length) {
//      byteBufferList.add(byte2unicode.get(text.charAt(i).toInt))
//    }
//    val byteBuffer = new Array[Byte](byteBufferList.size)
//    for (i <- 0 until byteBuffer.length) {
//      var byteString = byteBufferList.get(i)
//      if (byteString == null) byteString = " "
//      byteBuffer(i) = byteString.charAt(0).toByte
//    }
//    Chars.asList(StandardCharsets.UTF_8.decode(ByteBuffer.wrap(byteBuffer)).array).stream.map(String.valueOf).collect(Collectors.joining)
//  }
//}