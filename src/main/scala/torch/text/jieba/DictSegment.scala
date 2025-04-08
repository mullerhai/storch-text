package torch.text.jieba

import scala.collection.mutable
import scala.collection.mutable.*

/**
 * 词典树分段，表示词典树的一个分枝
 */
object DictSegment {
  // 公用字典表，存储汉字
  private val charMap = new mutable.HashMap[Character, Character](16, 0.95f)
  // 数组大小上限
  private val ARRAY_LENGTH_LIMIT = 3
}

class DictSegment(var nodeChar: Character) extends Comparable[DictSegment] {
  if (nodeChar == null) throw new IllegalArgumentException("参数为空异常，字符不能为空")
  // Map存储结构
  private var childrenMap: mutable.HashMap[Character, DictSegment] = new mutable.HashMap[Character, DictSegment]
  // 数组方式存储结构
  private var childrenArray: Array[DictSegment] = null //  Array[DictSegment]()
  // 当前节点存储的Segment数目
  // storeSize <=ARRAY_LENGTH_LIMIT ，使用数组存储， storeSize >ARRAY_LENGTH_LIMIT
  // ,则使用Map存储
  private var storeSize = 0
  // 当前DictSegment状态 ,默认 0 , 1表示从根节点到当前节点的路径表示一个词
  private var nodeState = 0

  /**
   * 匹配词段
   *
   * @param charArray
   * @param begin
   * @param length
   * @param searchHit
   * @return Hit
   */
  def matchChars(
                  charArray: Array[Char],
                  begin: Int,
                  length: Int,
                  searchHit: Hit = null
                ): Hit = {
    val hit = if (searchHit == null) {
      val newHit = new Hit()
      newHit.setBegin(begin)
      newHit
    } else {
      searchHit.setUnmatch()
      searchHit
    }
    hit.setEnd(begin)

    val keyChar = charArray(begin)
    var ds: DictSegment = null

    // 优先从数组查找
    if (childrenArray != null) {
      val keySeg = new DictSegment(keyChar)
      val pos = java.util.Arrays.binarySearch(
        childrenArray, 0, storeSize, keySeg,
        (a: DictSegment, b: DictSegment) => a.nodeChar.compareTo(b.nodeChar)
      )
      if (pos >= 0) ds = childrenArray(pos)
    } else if (childrenMap != null) {
      ds = childrenMap.getOrElse(keyChar, null)
    }

    if (ds != null) {
      if (length > 1) {
        ds.matchChars(charArray, begin + 1, length - 1, hit)
      } else if (length == 1) {
        if (ds.nodeState == 1) hit.setMatch()
        if (ds.hasNextNode) {
          hit.setPrefix()
          hit.setMatchedDictSegment(ds)
        }
        hit
      } else {
        hit
      }
    } else {
      hit
    }
  }

  def binarySearch0[A <: Comparable[A]](a: Array[A], fromIndex: Int, toIndex: Int, key: A): Int = {
    var low = fromIndex
    var high = toIndex - 1

    while (low <= high) {
      val mid = (low + high) >>> 1
      val midVal = a(mid)
      val cmp = midVal.compareTo(key)

      if (cmp < 0) {
        low = mid + 1
      } else if (cmp > 0) {
        high = mid - 1
      } else {
        return mid // 找到关键字
      }
    }
    -(low + 1) // 未找到，返回插入点
  }

  /**
   * 实现Comparable接口
   *
   * @param o
   * @return int
   */
  override def compareTo(o: DictSegment): Int = {
    // 对当前节点存储的char进行比较
    this.nodeChar.compareTo(o.nodeChar)
  }

  private[jieba] def getNodeChar = nodeChar

  /*
       * 判断是否有下一个节点
       */
  private[jieba] def hasNextNode = this.storeSize > 0


  /**
   * 匹配词段
   *
   * @param charArray
   * @return Hit
   */
  private[jieba] def matchChar(charArray: Array[Char]) = this.matchChars(charArray, 0, charArray.length, null)

  /**
   * 匹配词段
   *
   * @param charArray
   * @param begin
   * @param length
   * @return Hit
   */
  private[jieba] def matchChar(charArray: Array[Char], begin: Int, length: Int) = this.matchChars(charArray, begin, length, null)

  /**
   * 加载填充词典片段
   *
   * @param charArray
   */
  private[jieba] def fillSegment(charArray: Array[Char]): Unit = {
    this.fillSegment(charArray, 0, charArray.length, 1)
  }

  /**
   * 屏蔽词典中的一个词
   *
   * @param charArray
   */
  private[jieba] def disableSegment(charArray: Array[Char]): Unit = {
    this.fillSegment(charArray, 0, charArray.length, 0)
  }

  /**
   * 加载填充词典片段
   *
   * @param charArray
   * @param begin
   * @param length
   * @param enabled
   */
  private def fillSegment(charArray: Array[Char], begin: Int, length: Int, enabled: Int): Unit = {
    // 获取字典表中的汉字对象
    //    val beginChar = charArray(begin)
    val beginChar: Character = new Character(charArray(begin).toChar)
    var keyChar: Character = DictSegment.charMap.getOrElse(beginChar, null)
    // 字典中没有该字，则将其添加入字典
    if (keyChar == null) {
      DictSegment.charMap.put(beginChar, beginChar)
      keyChar = beginChar
    }
    // 搜索当前节点的存储，查询对应keyChar的keyChar，如果没有则创建
    val ds = lookforSegment(keyChar, enabled)
    if (ds != null) {
      // 处理keyChar对应的segment
      if (length > 1) {
        // 词元还没有完全加入词典树
        ds.fillSegment(charArray, begin + 1, length - 1, enabled)
      }
      else if (length == 1) {
        // 已经是词元的最后一个char,设置当前节点状态为enabled，
        // enabled=1表明一个完整的词，enabled=0表示从词典中屏蔽当前词
        ds.nodeState = enabled
      }
    }
  }

  /**
   * 查找本节点下对应的keyChar的segment *
   *
   * @param keyChar
   * @param create
   * =1如果没有找到，则创建新的segment ; =0如果没有找到，不创建，返回null
   * @return
   */
  private def lookforSegment(keyChar: Character, create: Int) = {
    var ds: DictSegment = null
    if (this.storeSize <= DictSegment.ARRAY_LENGTH_LIMIT) {
      // 获取数组容器，如果数组未创建则创建数组
      val segmentArray = getChildrenArray
      // 搜寻数组
      val keySegment = new DictSegment(keyChar)
      val position = binarySearch0(segmentArray, 0, this.storeSize, keySegment)
      if (position >= 0) ds = segmentArray(position)
      // 遍历数组后没有找到对应的segment
      if (ds == null && create == 1) {
        ds = keySegment
        if (this.storeSize < DictSegment.ARRAY_LENGTH_LIMIT) {
          // 数组容量未满，使用数组存储
          segmentArray(this.storeSize) = ds
          // segment数目+1
          this.storeSize += 1
          segmentArray.take(this.storeSize).sorted
          //          java.util.Arrays.sort[DictSegment](segmentArray, 0, this.storeSize)
        }
        else {
          // 数组容量已满，切换Map存储
          // 获取Map容器，如果Map未创建,则创建Map
          val segmentMap = getChildrenMap
          // 将数组中的segment迁移到Map中
          migrate(segmentArray, segmentMap)
          // 存储新的segment
          segmentMap.put(keyChar, ds)
          // segment数目+1 ， 必须在释放数组前执行storeSize++ ， 确保极端情况下，不会取到空的数组
          this.storeSize += 1
          // 释放当前的数组引用
          this.childrenArray = null
        }
      }
    }
    else {
      // 获取Map容器，如果Map未创建,则创建Map
      val segmentMap = getChildrenMap
      // 搜索Map
      ds = segmentMap.getOrElse(keyChar, null) //.get//.asInstanceOf[DictSegment]
      if (ds == null && create == 1) {
        // 构造新的segment
        ds = new DictSegment(keyChar)
        segmentMap.put(keyChar, ds)
        // 当前节点存储segment数目+1
        this.storeSize += 1
      }
    }
    ds
  }

  /**
   * 获取数组容器 线程同步方法
   */
  private def getChildrenArray = {
    if (this.childrenArray == null) this.synchronized {
      if (this.childrenArray == null) this.childrenArray = new Array[DictSegment](DictSegment.ARRAY_LENGTH_LIMIT)
    }
    this.childrenArray
  }

  /**
   * 获取Map容器 线程同步方法
   */
  private def getChildrenMap = {
    if (this.childrenMap == null) this.synchronized {
      if (this.childrenMap == null) this.childrenMap = new mutable.HashMap[Character, DictSegment](DictSegment.ARRAY_LENGTH_LIMIT * 2, 0.8f)
    }
    this.childrenMap
  }

  /**
   * 将数组中的segment迁移到Map中
   *
   * @param segmentArray
   */
  private def migrate(segmentArray: Array[DictSegment], segmentMap: mutable.HashMap[Character, DictSegment]): Unit = {
    for (segment <- segmentArray) {
      if (segment != null) segmentMap.put(segment.nodeChar, segment)
    }
  }
}

//  private[jieba] def `match`(charArray: Array[Char], begin: Int, length: Int, searchHit: Hit): Hit = {
//    if (searchHit == null) {
//      // 如果hit为空，新建
//      searchHit = new Hit
//      // 设置hit的其实文本位置
//      searchHit.setBegin(begin)
//    }
//    else {
//      // 否则要将HIT状态重置
//      searchHit.setUnmatch()
//    }
//    // 设置hit的当前处理位置
//    searchHit.setEnd(begin)
//    val keyChar = new Character(charArray(begin))
//    var ds: DictSegment = null
//    // 引用实例变量为本地变量，避免查询时遇到更新的同步问题
//    val segmentArray = this.childrenArray
//    val segmentMap = this.childrenMap
//    // STEP1 在节点中查找keyChar对应的DictSegment
//    if (segmentArray != null) {
//      // 在数组中查找
//      val keySegment = new DictSegment(keyChar)
//      val position = java.util.Arrays.binarySearch(segmentArray.asJava, 0, this.storeSize, keySegment)
//      if (position >= 0) ds = segmentArray(position)
//    }
//    else if (segmentMap != null) {
//      // 在map中查找
//      ds = segmentMap.get(keyChar).asInstanceOf[DictSegment]
//    }
//    // STEP2 找到DictSegment，判断词的匹配状态，是否继续递归，还是返回结果
//    if (ds != null) if (length > 1) {
//      // 词未匹配完，继续往下搜索
//      return ds.`match`(charArray, begin + 1, length - 1, searchHit)
//    }
//    else if (length == 1) {
//      // 搜索最后一个char
//      if (ds.nodeState == 1) {
//        // 添加HIT状态为完全匹配
//        searchHit.setMatch()
//      }
//      if (ds.hasNextNode) {
//        // 添加HIT状态为前缀匹配
//        searchHit.setPrefix()
//        // 记录当前位置的DictSegment
//        searchHit.setMatchedDictSegment(ds)
//      }
//      return searchHit
//    }
//    // STEP3 没有找到DictSegment， 将HIT设置为不匹配
//    searchHit
//  }