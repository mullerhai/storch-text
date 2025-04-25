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
//import java.nio.file.Path
//import java.util.Optional
//
//object GemmaTokenizer {
//  private val SPIECE_UNDERLINE = "▁"
//}
//
//class GemmaTokenizer(modelRoot: Path) extends BPETokenizer(modelRoot) {
//  
//  final private var byteFallbackEncodingOffset = 217
//
//  override protected def encodeCharacterAsToken(c: Byte): Long = java.lang.Byte.toUnsignedLong(c) + byteFallbackEncodingOffset
//
//  override protected def maybeDecodeTokenAsCharacter(id: Long): Optional[Character] = {
//    // Handle ascii codes (shifted in vocab)
//    if (model.byteFallback && id >= byteFallbackEncodingOffset && id < 256 + byteFallbackEncodingOffset) {
//      val c = (id - byteFallbackEncodingOffset).toChar
//      return Optional.of(c)
//    }
//    Optional.empty
//  }
//
//  override protected def preProcess(sentence: String): String = {
//    sentence.replace(" ", GemmaTokenizer.SPIECE_UNDERLINE)
//  }
//
//  override protected def postProcess(sentence: String): String = stripLeading(sentence) //.stripLineEnd.asInstanceOf[java.lang.String].stripLeading()
//
//  override protected def postProcessToken(decode: String): String = {
//    var decoded = if (decode == null) then model.unkToken else decode
//    decoded = decoded.replaceAll("</?s>", "")
//    decoded = decoded.replaceAll(GemmaTokenizer.SPIECE_UNDERLINE, " ")
//    decoded
//  }
//
//  def stripLeading(str: String): String =
//    str.dropWhile(_.isWhitespace) // 删除所有前导空白字符（空格、制表符、换行等）
//
//}