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
//import java.nio.file.Path
//import java.util.Optional
//import java.util.stream.Collectors
//
//object LlamaTokenizer {
//  private  val SPIECE_UNDERLINE = "▁"
//}
//
//class LlamaTokenizer(modelRoot: Path) extends BPETokenizer(modelRoot) {
//  
//  final private var byteFallbackEncodingOffset = this.getModel.vocabLookup.getOrDefault("<0x00>", -1L).intValue
//
//  override protected def encodeCharacterAsToken(c: Byte): Long = java.lang.Byte.toUnsignedLong(c) + Math.max(byteFallbackEncodingOffset, 0)
//
//  override protected def maybeDecodeTokenAsCharacter(id: Long): Optional[Character] = {
//    // Handle ascii codes (shifted by N in vocab)
//    if (model.byteFallback && byteFallbackEncodingOffset > 0 && id >= byteFallbackEncodingOffset && id < 256 + byteFallbackEncodingOffset) {
//      val c = (id - byteFallbackEncodingOffset).toChar
//      return Optional.of(c)
//    }
//    Optional.empty
//  }
//
//  override protected def preProcess(sentence: String): String = {
//    var sentences =if (this.model.normalizer != null)then model.normalizer.normalize(sentence) else sentence
//    if (model.isLegacy && !model.byteFallback) sentences = sentences.codePoints.map((c: Int) => alteredBytes.getOrDefault(c, c)).mapToObj(Character.toString).collect(Collectors.joining)
//    sentences
//  }
//
//  override protected def postProcess(sentence: String): String = sentence.stripLeading
//
//  override protected def postProcessToken(decode: String): String = {
//    var decoded = if (decode == null) then model.unkToken else decode
////    if (decoded == null) decoded = model.unkToken
//    decoded = decoded.replaceAll("</?s>", "")
//    decoded = decoded.replaceAll(LlamaTokenizer.SPIECE_UNDERLINE, " ")
//    if (model.isLegacy && !model.byteFallback) decoded = decoded.codePoints.map((c: Int) => alteredBytes.inverse.getOrDefault(c, c)).mapToObj(Character.toString).collect(Collectors.joining)
//    decoded
//  }
//}