package torch.text

import org.bytedeco.javacpp._
import org.bytedeco.sentencepiece._

/**
 * To try encoding you can download an existing model, i.e.
 * wget https://nlp.h-its.org/bpemb/en/en.wiki.bpe.vs10000.model
 * mvn compile exec:java exec.args="en.wiki.bpe.vs10000.model"
 */
object SentencePieceExample {
  def main(args: Array[String]): Unit = {
    try {
//      args(0)="1"
      val processor = new SentencePieceProcessor 
      val wikiModelPath =  "D:\\data\\storch-text\\src\\main\\resources\\en.wiki.bpe.vs10000.model"
      val bpeModelPath =  "D:\\data\\storch-text\\src\\main\\resources\\xlmr.sentencepiece.bpe.model"
      val status = processor.Load(bpeModelPath)
      if (!status.ok) throw new RuntimeException(status.ToString)
      val ids = new IntVector
      processor.Encode("hello world!", ids)
      for (id <- ids.get) {
        System.out.print(id + " ")
      }
      System.out.println()
      val text = new BytePointer("")
      processor.Decode(ids, text)
      System.out.println(text.getString)
    } catch {
      case e: Exception =>
        e.printStackTrace()
    }
  }
}