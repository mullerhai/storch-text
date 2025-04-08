package torch.text.jieba

class Keyword(var _name: String, var _tfidfvalue: Double)
  extends Ordered[Keyword] {

  // 精度处理（四舍五入保留4位小数）
  private val roundedValue = BigDecimal(_tfidfvalue)
    .setScale(4, BigDecimal.RoundingMode.HALF_UP)
    .toDouble

  // 公开访问方法（Scala风格）
  def getName: String = _name

  def name_=(newName: String): Unit = _name = newName

  def tfidfvalue_=(newValue: Double): Unit = {
    _tfidfvalue = BigDecimal(newValue)
      .setScale(4, BigDecimal.RoundingMode.HALF_UP)
      .toDouble
  }

  override def compareTo(o: Keyword): Int = if (this.getTfidfvalue > o.getTfidfvalue) -1
  else if (this.getTfidfvalue == o.getTfidfvalue) 0
  else -1

  def getTfidfvalue: Double = roundedValue

  override def compare(that: Keyword): Int = if (this.getTfidfvalue > that.getTfidfvalue) -1
  else if (this.getTfidfvalue == that.getTfidfvalue) 0
  else -1

  // 比较逻辑（降序排列）
  //  override def compare(that: Keyword): Int = {
  //
  //    Ordering.Double.reverse.compare(this.tfidfvalue, that.tfidfvalue)
  //  }

  // 基于名称的相等性判断
  override def equals(obj: Any): Boolean = obj match {
    case k: Keyword => this._name == k._name
    case _ => false
  }

  // 包含名称和值的哈希码
  override def hashCode(): Int = {
    31 * _name.hashCode + java.lang.Double.hashCode(_tfidfvalue)
  }

  // 优化后的toString
  override def toString: String =
    s"Keyword($_name., ${"%.4f".format(_tfidfvalue)})"


}

object Keyword {
  // 辅助构造函数（可选）
  def apply(name: String, tfidfvalue: Double): Keyword =
    new Keyword(name, tfidfvalue)
}


//class Keywords(var name: String,var tfidfvalue: Double) extends Comparable[Keyword] { // tfidf值只保留3位小数
//  this.tfidfvalue = tfidfvalue * 10000.round.toDouble / 10000
