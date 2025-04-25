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
//
//import com.google.common.base.Preconditions
//import com.google.common.collect.{BiMap, HashBiMap, ImmutableBiMap}
//import org.slf4j.{Logger, LoggerFactory}
//import scala.collection.mutable.ListBuffer
//import java.io.IOException
//import java.nio.ByteBuffer
//import java.nio.charset.StandardCharsets
//import java.nio.file.Path
//import java.util.*
//import java.util.stream.Collectors
//
///**
// * Byte Pair Encoding tokenizer
// */
//object BPETokenizer {
//  protected val logger: Logger = LoggerFactory.getLogger(classOf[BPETokenizer])
//  var alteredBytes: BiMap[Integer, Integer] = null // Codepoint and Token mapping needed for legacy mode
//  try
//  // https://github.com/openai/gpt-2/blob/master/src/encoder.py#L19
//  val tmpAlteredBytes: BiMap[Integer, Integer] = HashBiMap.create
//  var i = 0
//  for (c <- 0 until 256) {
//    if ((c < '!' || c > '~') && (c < '¡' || c > '¬') && (c < '®' || c > 'ÿ')) {
//      val codepoint = {
//        i += 1; i - 1
//      } + 256
//      tmpAlteredBytes.put(c, codepoint)
//    }
//  }
//  alteredBytes = ImmutableBiMap.copyOf(tmpAlteredBytes)
//}
//
//abstract class BPETokenizer protected(modelRoot: Path) extends Tokenizer {
//  Preconditions.checkArgument(modelRoot.resolve("tokenizer.json").toFile.exists, "No tokenizer.json found in " + modelRoot)
//  final protected var model: TokenizerModel = null
//  final protected var promptSupport: PromptSupport = null
//  final protected val decodeBuffer = ByteBuffer.allocate(4)
//  try {
//    this.model = SafeTensorSupport.loadTokenizer(modelRoot)
//    this.promptSupport = new PromptSupport(model)
//  } catch {
//    case e: IOException =>
//      throw new RuntimeException(e)
//  }
//
//
//  override def getModel: TokenizerModel = model
//
//  override def tokenize(sentence: String): Seq[String] = {
//    if (sentence.isEmpty) return Collections.emptyList
//    if (model.preTokenizer == null && model.addedTokenPattern == null) Collections.singletonList(sentence)
//    val sentencePieces = new ListBuffer[String]
//    if (model.addedTokenPattern != null) {
//      // Split the sentence into pieces using the added token pattern
//      // Any non-added token is split into pieces using the pre-tokenizer
//      val pieces = TokenizerModel.split(model.addedTokenPattern, sentence, 0, true)
//      for (piece <- pieces) {
//        if (!piece.isEmpty) if (model.addedTokens.containsKey(piece)) sentencePieces.add(piece)
//        else if (model.preTokenizer != null) sentencePieces.addAll(model.preTokenizer.pretokenize(piece))
//        else sentencePieces.add(piece)
//      }
//    }
//    else if (model.preTokenizer != null) sentencePieces.addAll(model.preTokenizer.pretokenize(sentence))
//    else sentencePieces.add(sentence)
//    sentencePieces
//  }
//
//  protected def preProcess(sentence: String): String = sentence
//
//  override def encode(rawSentence: String): Array[Long] = {
//    val sentencePieces = tokenize(rawSentence)
//    val allTokens = new ListBuffer[Long]
////    import scala.collection.JavaConversions.*
//    for (sentence <- sentencePieces) {
//      if (model.addedTokens != null && model.addedTokens.containsKey(sentence)) {
//        allTokens.appended(model.addedTokens.get(sentence))
//        continue //todo: continue is not supported
//      }
//      val tokens = new ListBuffer[Long]
//      sentence = preProcess(sentence)
//      val codes = sentence.codePoints.toArray
//      for (i <- 0 until codes.length) {
//        val c = Character.toString(codes(i))
//        val id = model.vocabLookup.get(c)
//        if (id != null) {
//          // we found this codepoint in vocab, add it as a token
//          // logger.debug("{} -> {}", c, id);
//          tokens.appended(id)
//        }
//        else if (model.byteFallback) {
//          // byte_fallback encoding: just encode each byte as a token
//          val code = Character.toString(codes(i))
//          val chars = code.getBytes(StandardCharsets.UTF_8)
//          for (k <- 0 until chars.length) {
//            val token = encodeCharacterAsToken(chars(k))
//            // logger.debug("byte {} -> {}", Byte.toUnsignedInt(chars[k]), token);
//            tokens.appended(token)
//          }
//        }
//        else if (model.unkToken != null) tokens.appended(model.vocabLookup.get(model.unkToken))
//      }
//      // merge the best consecutive tuple each iteration,
//      // until we can't find any more pairs to merge
//      while (true) {
//        var bestId = -1
//        var bestIdx = -1
//        var bestRank = Long.MaxValue
//        for (i <- 0 until tokens.size - 1) {
//          // check if we can merge the pair (tokens[i], tokens[i+1])
//          val token1 = decodeInternal(tokens(i))
//          val token2 = decodeInternal(tokens(i + 1))
//          val merge2 = String.format("%s %s", token1, token2)
//          val merge3 = String.format("%s%s", token1, token2)
//          if (model.merges.containsKey(merge2)) {
//            val id = model.vocabLookup.get(merge3)
//            if (id != null) {
//              // Check if this merge has a better rank (i.e., lower rank number)
//              val rank = model.merges.get(merge2)
//              if (rank < bestRank) {
//                // this merge pair exists in vocab! record its position
//                bestId = id
//                bestIdx = i
//                bestRank = rank
//              }
//            }
//          }
//        }
//        if (bestIdx == -1) {
//          break //todo: break is not supported
//          // we couldn't find any more pairs to merge, so we're done
//        }
//        // merge the consecutive pair (best_idx, best_idx+1) into new token best_id
//        tokens.insert(bestIdx.toInt, bestId)
//        // delete token at position best_idx+1, shift the entire sequence back 1
//        tokens.remove(bestIdx.toInt + 1)
//      }
//      allTokens.addAll(tokens)
//    }
//    allTokens.stream.mapToLong((s: Long) => s).toArray
//  }
//
//  protected def postProcessToken(decoded: String): String = {
//    if (decoded == null) decoded = model.unkToken
//    decoded
//  }
//
//  override def decode(id: Long): String = maybeDecodeTokenAsCharacter(id).map((c: Character) => {
//    def foo(c: Character) = {
//      // We have a continuation byte or are buffering them
//      if (Character.isUnicodeIdentifierPart(c) || decodeBuffer.remaining < 4) {
//        decodeBuffer.put(c.charValue.toByte)
//        // Unicode symbol is ready
//        if (decodeBuffer.remaining == 0) {
//          val s = new String(decodeBuffer.array)
//          decodeBuffer.rewind
//          return s
//        }
//        return ""
//      }
//      Character.toString(c)
//    }
//
//    foo(c)
//  }).orElseGet(() => postProcessToken(model.vocabLookup.inverse.get(id)))
//
//  protected def encodeCharacterAsToken(c: Byte): Long
//
//  protected def maybeDecodeTokenAsCharacter(id: Long): Optional[Character]
//
//  // Only used for merging
//  protected def decodeInternal(id: Long): String = maybeDecodeTokenAsCharacter(id).map(Object.toString).orElseGet(() => {
//    var s = model.vocabLookup.inverse.get(id)
//    if (s == null) s = model.unkToken
//    s
//  })
//
//  protected def postProcess(sentence: String): String = sentence
//
//  override def decode(ids: Array[Long]): String = postProcess(util.Arrays.stream(ids).mapToObj(this.decode).collect(Collectors.joining))
//
//  override def promptSupport: Optional[PromptSupport] = if (model.promptTemplates.isPresent) Optional.of(promptSupport)
//  else Optional.empty
//}