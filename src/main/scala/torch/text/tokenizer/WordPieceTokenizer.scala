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
////import safetensors.SafeTensorSupport
////import safetensors.prompt.PromptSupport
//import com.google.common.base.Preconditions
//
//import java.io.IOException
//import java.nio.file.Path
//import java.util
//import java.util.Optional
//import java.util.stream.{Collectors, Stream}
//
///**
// * WordPiece tokenizer
// *
// * @see <a href="https://github.com/google-research/bert/blob/master/tokenization.py">...</a>
// */
//object WordPieceTokenizer {
//  protected val sepString = "[SEP]"
//  protected val clsString = "[CLS]"
//  protected val unkString = "[UNK]"
//
//  private[tokenizer] def isControl(c: Integer): Boolean = {
//    // These are technically control characters but we count them as whitespace characters.
//    if ((c eq '\t') || (c eq '\n') || (c eq '\r')) return false
//    Character.isISOControl(c)
//  }
//
//  private[tokenizer] def isPunctuation(cp: Integer): Boolean = {
//    if ((cp >= 33 && cp <= 47) || (cp >= 58 && cp <= 64) || (cp >= 91 && cp <= 96) || (cp >= 123 && cp <= 126)) return true
//    val t = Character.getType(cp)
//    if (t >= 20 && t <= 24) return true
//    false
//  }
//}
//
//class WordPieceTokenizer(modelRoot: Path) extends Tokenizer {
//  Preconditions.checkArgument(modelRoot.resolve("tokenizer.json").toFile.exists, "No tokenizer.json found in " + modelRoot)
//
//  final protected var model: TokenizerModel = null
////  final protected var promptSupport: PromptSupport = null
//
//  final protected var sepToken = model.vocabLookup.get(WordPieceTokenizer.sepString)
//  final protected var clsToken = model.vocabLookup.get(WordPieceTokenizer.clsString)
//  final protected var unkToken = model.vocabLookup.get(WordPieceTokenizer.unkString)
//  try {
//    this.model = SafeTensorSupport.loadTokenizer(modelRoot)
//    Preconditions.checkArgument(model.`type` == null || model.`type`.equalsIgnoreCase("WordPiece"), "Invalid model type: " + model.`type`)
////    this.promptSupport = new PromptSupport(model)
//  } catch {
//    case e: IOException =>
//      throw new RuntimeException(e)
//  }
//
//  override def getModel: TokenizerModel = model
//
//  override def tokenize(sentences: String): util.List[String] = {
//    var sentence = preProcess(sentences)
//    val whitespaceSplits = sentence.split("\\s+")
//    val tokens = new util.ArrayList[String]
//    tokens.add(WordPieceTokenizer.clsString)
//    val stringList = util.Arrays.stream(whitespaceSplits).flatMap(this.splitByPunctuation).map((str: String) => if (str.length > 200) model.unkToken
//    else str).flatMap((str: String) => {
//      var isBad = false
//      val subTokens = new util.ArrayList[String]
//      var start = 0
//      while (start < str.length) {
//        var end = str.length
//        var curSubStr: String = null
//        while (start < end) {
//          var substr = str.substring(start, end)
//          if (start > 0) substr = "##" + substr
//          if (model.vocabLookup.containsKey(substr)) {
//            curSubStr = substr
//            break //todo: break is not supported
//          }
//          end -= 1
//        }
//        if (curSubStr == null) {
//          isBad = true
//          break //todo: break is not supported
//        }
//        subTokens.add(curSubStr)
//        start = end
//      }
//      if (isBad) subTokens.add(model.unkToken)
//      subTokens.stream
//    }).collect(Collectors.toList)
//    tokens.addAll(stringList)
//    tokens.add(WordPieceTokenizer.sepString)
//    tokens
//  }
//
//  protected def preProcess(sentence: String): String = {
//    sentence = sentence.toLowerCase.strip
//    cleanText(sentence)
//  }
//
//  private[tokenizer] def cleanText(sentence: String) = sentence.codePoints.map((c: Int) => {
//    def foo(c: Int) = {
//      if (c == 0 || c == 0xfffd || WordPieceTokenizer.isControl(c)) return -1
//      if (Character.isWhitespace(c)) return ' '
//      c
//    }
//
//    foo(c)
//  }).filter((c: Int) => c != -1).mapToObj(Character.toString).collect(Collectors.joining)
//
//  private[tokenizer] def splitByPunctuation(str: String) = {
//    val result = new util.ArrayList[String]
//    var start = 0
//    var offset = 0
//    while (offset < str.length) {
//      val codepoint = str.codePointAt(offset)
//      if (WordPieceTokenizer.isPunctuation(codepoint)) {
//        if (offset != start) result.add(str.substring(start, offset))
//        result.add(str.substring(offset, offset + Character.charCount(codepoint)))
//        start = offset + Character.charCount(codepoint)
//      }
//      offset += Character.charCount(codepoint)
//    }
//    // Add the remaining part if there's any
//    if (start != str.length) result.add(str.substring(start))
//    result.stream
//  }
//
//  override def encode(sentence: String): Array[Long] = tokenize(sentence).stream.mapToLong((s: String) => model.vocabLookup.get(s)).toArray
//
//  protected def postProcessToken(decoded: String): String = {
//    if (decoded.startsWith("##")) return decoded.substring(2)
//    " " + decoded
//  }
//
//  override def decode(id: Long): String = postProcessToken(model.vocabLookup.inverse.get(id))
//
//  protected def postProcess(sentence: String): String = sentence.trim
//
//  override def decode(ids: Array[Long]): String = postProcess(util.Arrays.stream(ids).mapToObj(this.decode).collect(Collectors.joining))
//
////  override def promptSupport: Optional[PromptSupport] = if (model.promptTemplates.isPresent) Optional.of(promptSupport)
////  else Optional.empty
//}