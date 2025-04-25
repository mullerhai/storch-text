package torch.text.tokenizer

import java.util
import java.util.Optional
import scala.collection.mutable.ListBuffer

class CLIPTokenizer extends Tokenizer{

  
  /**
   * Get the model for this tokenizer (expert mode)
   *
   * @return tokenizer model
   */
//  override def getModel: TokenizerModel = ???

  /**
   * Tokenize a sentence
   *
   * @param sentence
   * @return list of token strings
   */
  override def tokenize(sentence: String): ListBuffer[String] = ???

  /**
   * Encode a sentence into a list of token ids
   *
   * @param sentence
   * @return list of token ids
   */
  override def encode(sentence: String): Array[Long] = ???

  /**
   * Decode a token id into its string representation
   *
   * @param id
   * @return token string
   */
  override def decode(id: Long): String = ???

  /**
   * Decode a list of token ids into their string representation
   *
   * @param ids list of token ids
   * @return list of token strings
   */
  override def decode(ids: Array[Long]): String = ???

  /**
   * Get the prompt support for this tokenizer model if it exists
   *
   * @return prompt support
   */
  override def promptSupport: Optional[PromptSupport] = ???
}
