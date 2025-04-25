/*
 * Copyright 2024 T Jake Luciani
 *
 * The Storch-Text Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package torch.text.tokenizer

//import com.github.tjake.Storch-Text.safetensors.prompt.PromptSupport

import java.util
import java.util.Optional
import scala.collection.mutable.ListBuffer
case class PromptSupport(prompt: String)
/**
 * Tokenizer interface
 */
trait Tokenizer {
  /**
   * Tokenize a sentence
   *
   * @param sentence
   * @return list of token strings
   */
  def tokenize(sentence: String): ListBuffer[String]

  /**
   * Encode a sentence into a list of token ids
   *
   * @param sentence
   * @return list of token ids
   */
  def encode(sentence: String): Array[Long]

  /**
   * Decode a token id into its string representation
   *
   * @param id
   * @return token string
   */
  def decode(id: Long): String

  /**
   * Decode a list of token ids into their string representation
   *
   * @param ids list of token ids
   * @return list of token strings
   */
  def decode(ids: Array[Long]): String

  /**
   * Get the prompt support for this tokenizer model if it exists
   *
   * @return prompt support
   */
  def promptSupport: Optional[PromptSupport]

  /**
   * Get the model for this tokenizer (expert mode)
   *
   * @return tokenizer model
   */
//  def getModel: TokenizerModel
}