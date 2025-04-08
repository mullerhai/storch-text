package torch.text.jieba

case class Pair[K](var key: K, var freq: Double = 0.0) {
  override def toString: String = "Candidate [key=" + key + ", freq=" + freq + "]"

}