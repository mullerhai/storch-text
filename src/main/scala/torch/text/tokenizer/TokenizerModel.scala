///*
// * Copyright 2024 T Jake Luciani
// *
// * The Storch-Text Project licenses this file to you under the Apache License,
// * version 2.0 (the "License"); you may not use this file except in compliance
// * with the License. You may obtain a copy of the License at:
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
// * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
// * License for the specific language governing permissions and limitations
// * under the License.
// */
//package torch.text.tokenizer
//
//import com.fasterxml.jackson.annotation.{JsonCreator, JsonProperty}
//
////import tokenizer.BPETokenizer.alteredBytes
//import com.google.common.base.Preconditions
//import com.google.common.collect.*
//import org.slf4j.{Logger, LoggerFactory}
//import scala.jdk.CollectionConverters._
//import java.text.Normalizer
//import java.util.*
//import java.util.regex.{Matcher, Pattern}
//import java.util.stream.Collectors
//import scala.collection.mutable.ListBuffer
///**
// * Tokenizer model, loosely based on Huggingface's Tokenizer format
// *
// * @see <a href="https://huggingface.co/transformers/main_classes/tokenizer.html">Huggingface Tokenizer</a>
// *
// *      This class also holds the prompt templates
// * @see <a href="https://huggingface.co/docs/transformers/main/en/chat_templating#templates-for-chat-models">Chat Templating</a>
// * @see PromptSupport
// */
//object TokenizerModel {
//  private val logger = LoggerFactory.getLogger(classOf[TokenizerModel])
//  private val gpt2Pattern = java.util.regex.Pattern.compile("(?i:'s|'t|'re|'ve|'m|'ll|'d)|[^\\r\\n\\p{L}\\p{N}]?\\p{L}+|\\p{N}| ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*|\\s*[\\r\\n]+|\\s+(?!\\S)|\\s+")
//
//  // Splitter for added token pattern (optionally with delimiters)
//  private[tokenizer] def split(p: Pattern, input: CharSequence, limit: Int, withDelimiters: Boolean): Array[String] = {
//    var matchCount = 0
//    var index = 0
//    val matchLimited = limit > 0
//    val matchList = new ListBuffer[String]
//    val m = p.matcher(input)
//    // Add segments before each match found
//    while (m.find) if (!matchLimited || matchCount < limit - 1) {
//      if (index == 0 && index == m.start && m.start == m.end) {
//        // no empty leading substring included for zero-width match
//        // at the beginning of the input char sequence.
//        continue //todo: continue is not supported
//      }
//      val `match` = input.subSequence(index, m.start).toString
//      matchList.add(`match`)
//      index = m.end
//      if (withDelimiters) matchList.add(input.subSequence(m.start, index).toString)
//      matchCount += 1
//    }
//    else if (matchCount == limit - 1) { // last one
//      val `match` = input.subSequence(index, input.length).toString
//      matchList.add(`match`)
//      index = m.end
//      matchCount += 1
//    }
//    // If no match was found, return this
//    if (index == 0) return Array[String](input.toString)
//    // Add remaining segment
//    if (!matchLimited || matchCount < limit) matchList.add(input.subSequence(index, input.length).toString)
//    // Construct result
//    var resultSize = matchList.size
//    if (limit == 0) while (resultSize > 0 && matchList.get(resultSize - 1).isEmpty) resultSize -= 1
//    val result = new Array[String](resultSize)
//    matchList.subList(0, resultSize).toArray(result)
//  }
//
//  class Normalizer @JsonCreator(val `type`: String, @JsonProperty("normalizers") normalizerItems: util.List[TokenizerModel.NormalizerItem]) {
//
//    final var normalizerItems: ListBuffer[TokenizerModel.NormalizerItem] = null
//    this.normalizerItems = if (normalizerItems == null) Collections.emptyList
//    else ImmutableList.copyOf(normalizerItems)
//
//    def normalize(sentence: String): String = {
//      if (normalizerItems.isEmpty) return sentence
//      Preconditions.checkArgument(`type`.equalsIgnoreCase("Sequence"), "Invalid normalizer type: " + `type`)
////      import scala.collection.JavaConversions.*
//      for (item <- normalizerItems) {
//        sentence = item.normalize(sentence)
//      }
//      sentence
//    }
//  }
//
//  class NormalizerItem @JsonCreator(val `type`: String, val prepend: String, val pattern: util.Map[String, String], val content: String) {
//    def normalize(sentence: String): String = `type` match {
//      case "Replace" =>
//        replace(sentence)
//      case "Prepend" =>
//        prepend(sentence)
//      case "NFC" =>
//      case "NFKC" =>
//      case "NFD" =>
//      case "NFKD" =>
//        formNormalize(sentence)
//      case _ =>
//        throw new IllegalArgumentException("Invalid normalizer type: " + `type`)
//    }
//
//    private def formNormalize(sentence: String) = {
//      val form = java.text.Normalizer.Form.valueOf(`type`)
//      java.text.Normalizer.normalize(sentence, form)
//    }
//
//    private def replace(sentence: String) = {
//      import scala.collection.JavaConversions.*
//      for (entry <- pattern.entrySet) {
//        if (!entry.getKey.equalsIgnoreCase("String")) logger.warn("Ignoring unknown pattern key: " + entry.getKey)
//        sentence = sentence.replaceAll(entry.getValue, content)
//      }
//      sentence
//    }
//
//    private def prepend(sentence: String) = prepend + sentence
//  }
//
//  // PreTokenizer class
//  class PreTokenizer @JsonCreator(val `type`: String,
//                                  val replacement: String, 
//                                  val prependScheme: String,
//                                  @JsonProperty("pretokenizers") pretokenizers: util.List[TokenizerModel.PretokenizerItem]) {
//
//    final var isLegacy= this.pretokenizers.stream.map((p: TokenizerModel.PretokenizerItem) => p.`type`).anyMatch((t: String) => t == "ByteLevel")
//    final var pretokenizers: util.List[TokenizerModel.PretokenizerItem] = null
//    this.pretokenizers = if (pretokenizers == null) Collections.emptyList
//    else ImmutableList.copyOf(pretokenizers)
//
//    def pretokenize(sentence: String): util.List[String] = {
//      if (`type`.equalsIgnoreCase("MetaSpace")) {
//        if (prependScheme.equalsIgnoreCase("first")) sentence = " " + sentence
//        return Collections.singletonList(sentence.replaceAll("[ \t]+", replacement))
//      }
//      if (pretokenizers.isEmpty) return Collections.singletonList(sentence)
//      Preconditions.checkArgument(`type`.equalsIgnoreCase("Sequence"), "Invalid pre-tokenizer type: " + `type`)
//      var pieces = util.List.of(sentence)
//      var tmp = new util.ArrayList[String]
//      import scala.collection.JavaConversions.*
//      for (item <- pretokenizers) {
//        import scala.collection.JavaConversions.*
//        for (piece <- pieces) {
//          tmp.addAll(item.pretokenize(piece))
//        }
//        pieces = tmp
//        tmp = new util.ArrayList[String]
//      }
//      pieces
//    }
//  }
//
//  // PretokenizerItem class
//  class PretokenizerItem @JsonCreator(val `type`: String, 
//                                      val pattern: TokenizerModel.Pattern, 
//                                      val behavior: String, 
//                                      val invert: Boolean,
//                                      val individual_digits: Boolean, 
//                                      val add_prefix_space: Boolean, 
//                                      val trim_offsets: Boolean, 
//                                      val use_regex: Boolean) {
//    def pretokenize(sentence: String): util.List[String] = `type` match {
//      case "Split" =>
//        splitRegex(sentence)
//      case "Digits" =>
//        splitDigits(sentence)
//      case "ByteLevel" =>
//        // if (use_regex) return splitGpt2(sentence);
//        // Rather than deal with this, we'll just force byte fallback (only difference is how unk is
//        // handled)
//        Collections.singletonList(sentence)
//      case _ =>
//        throw new IllegalArgumentException("Invalid pre-tokenizer type: " + `type`)
//    }
//
//    private def byteLevel(sentence: String) = util.List.of(sentence.codePoints.map((c: Int) => alteredBytes.getOrDefault(c, c)).mapToObj(Character.toString).collect(Collectors.joining))
//
//    private def splitGpt2(sentence: String) = util.List.of(gpt2Pattern.split(sentence))
//
//    private def splitRegex(s: String) = {
//      val m = pattern.regex.matcher(s)
//      val ret = new util.ArrayList[String]
//      var start = 0
//      while (m.find) {
//        val r = s.substring(start, m.start)
//        if (!r.isEmpty) ret.add(r)
//        ret.add(m.group)
//        start = m.end
//      }
//      val p = if (start >= s.length) ""
//      else s.substring(start)
//      if (!p.isEmpty) ret.add(p)
//      ret
//    }
//
//    private def splitDigits(sentence: String) = util.List.of(sentence.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)"))
//  }
//
//  // Pattern class
//  class Pattern @JsonCreator(@JsonProperty("Regex") regex: String) {
//    this.regex = java.util.regex.Pattern.compile(regex)
//    final var regex: Pattern = null
//  }
//}
//
//class TokenizerModel @JsonCreator(@JsonProperty("type") val `type`: String,
//                                  @JsonProperty("unk_token") val unkToken: String,
//                                  @JsonProperty("fuse_unk") val fuseUnk: Boolean,
//                                  @JsonProperty("byte_fallback") val byteFallback: Boolean,
//                                  @JsonProperty("vocab") vocabLookup: util.Map[String, Long],
//                                  @JsonProperty("ignore_merges") ignoreMerges: Boolean,
//                                  @JsonProperty("merges") merges: util.List[AnyRef]) {
//
//  @JsonProperty("vocab") final var vocabLookup: BiMap[String, Long] = null
//  @JsonProperty("merges") final var merges: util.Map[String, Long] = null
//  private var preTokenizer: TokenizerModel.PreTokenizer = null
//  private var normalizer: TokenizerModel.Normalizer = null
//  private var addedTokens = HashBiMap.create
//  private var specialTokens = HashBiMap.create
//  private var addedTokenPattern: Pattern = null
//  // This is pretty much a hack to support the legacy tokenizer
//  private var legacy = false
//  private var promptTemplates = Optional.empty
//  private var hasToolSupport = false
//  private var eosToken = ""
//  private var bosToken = ""
//  final private var ignoreMerges = false
//  this.vocabLookup = HashBiMap.create(vocabLookup)
//  this.ignoreMerges = ignoreMerges != null && ignoreMerges
//  this.merges = new util.HashMap[String, Long]
//  if (merges != null) for (i <- 0 until merges.size) {
//    if (merges.get(i).isInstanceOf[String]) this.merges.put(merges.get(i).asInstanceOf[String], i.toLong)
//    else if (merges.get(i).isInstanceOf[util.List[_]]) {
//      val merge = merges.get(i).asInstanceOf[util.List[String]]
//      this.merges.put(merge.get(0) + " " + merge.get(1), i.toLong)
//    }
//    else throw new IllegalArgumentException("Invalid merge format: " + merges.get(i))
//  }
//
//  def preTokenizer: TokenizerModel.PreTokenizer = preTokenizer
//
//  def setPreTokenizer(preTokenizer: TokenizerModel.PreTokenizer): Unit = {
//    if (preTokenizer != null) {
//      this.preTokenizer = preTokenizer
//      this.legacy = preTokenizer.isLegacy
//    }
//  }
//
//  def normalizer: TokenizerModel.Normalizer = normalizer
//
//  def setNormalizer(normalizer: TokenizerModel.Normalizer): Unit = {
//    this.normalizer = normalizer
//  }
//
//  def setAddedTokens(addedTokens: ListBuffer[Map[String, AnyRef]]): Unit = {
//    if (addedTokens != null && !addedTokens.isEmpty) {
//
//      for (token <- addedTokens) {
//        this.addedTokens.put(token.get("content").asInstanceOf[String], token.get("id").asInstanceOf[Integer].longValue)
//        this.vocabLookup.put(token.get("content").asInstanceOf[String], token.get("id").asInstanceOf[Integer].longValue)
//        if (token.containsKey("special") && token.get("special").asInstanceOf[Boolean]) this.specialTokens.put(token.get("content").asInstanceOf[String], token.get("id").asInstanceOf[Integer].longValue)
//      }
//      // Lock down the added tokens
//      this.addedTokens = ImmutableBiMap.copyOf(this.addedTokens)
//      this.specialTokens = ImmutableBiMap.copyOf(this.specialTokens)
//      // Create a regular expression from the list of delimiters
//      val regex = new lang.StringBuilder
//      val delimiters = new util.ArrayList[String](this.addedTokens.keySet)
//      for (i <- 0 until delimiters.size) {
//        if (i != 0) regex.append("|")
//        regex.append(java.util.regex.Pattern.quote(delimiters.get(i)))
//      }
//      this.addedTokenPattern = java.util.regex.Pattern.compile(regex.toString)
//    }
//  }
//
//  def ignoreMerges: Boolean = ignoreMerges
//
//  def addedTokens: Map[String, Long] = addedTokens
//
//  def addedTokenPattern: Pattern = addedTokenPattern
//
//  def isLegacy: Boolean = legacy
//
//  def setLegacy(legacy: Boolean): Unit = {
//    this.legacy = legacy
//  }
//
//  def promptTemplates: Optional[Map[String, String]] = promptTemplates
//
//  def setPromptTemplates(promptTemplates: Map[String, String]): Unit = {
//    if (promptTemplates != null) {
//      hasToolSupport = promptTemplates.values.stream.anyMatch((s: String) => s.toLowerCase.contains("tools"))
//      this.promptTemplates = Optional.of(promptTemplates)
//    }
//  }
//
//  def hasToolSupport: Boolean = hasToolSupport
//
//  def setEosToken(eosToken: String): Unit = {
//    this.eosToken = eosToken
//  }
//
//  def eosToken: String = eosToken
//
//  def setBosToken(bosToken: String): Unit = {
//    this.bosToken = bosToken
//  }
//
//  def bosToken: String = bosToken
//
//  def isSpecialToken(token: Long): Boolean = specialTokens.containsValue(token)
//
//  def isSpecialToken(token: String): Boolean = specialTokens.containsKey(token)
//}