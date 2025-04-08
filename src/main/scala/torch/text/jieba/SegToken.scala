package torch.text.jieba

class SegToken(var word: String, var startOffset: Int, var endOffset: Int) {
  override def toString: String = "[" + word + ", " + startOffset + ", " + endOffset + "]"
}