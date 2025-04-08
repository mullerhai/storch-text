/**
 *
 * IK 中文分词  版本 5.0
 * IK Analyzer release 5.0
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * 源代码由林良益(linliangyi2005@gmail.com)提供
 * 版权声明 2012，乌龙茶工作室
 * provided by Linliangyi and copyright 2012 by Oolong studio
 *
 */
package torch.text.jieba

/**
 * 表示一次词典匹配的命中
 */
object Hit {
  //Hit不匹配
  private val UNMATCH = 0x00000000
  //Hit完全匹配
  private val MATCH = 0x00000001
  //Hit前缀匹配
  private val PREFIX = 0x00000010
}

class Hit { //该HIT当前状态，默认未匹配
  private var hitState = Hit.UNMATCH
  //记录词典匹配过程中，当前匹配到的词典分支节点
  private var matchedDictSegment: DictSegment = null
  /*
     * 词段开始位置
     */
  private var begin = 0
  /*
     * 词段的结束位置
     */
  private var end = 0

  /**
   * 判断是否完全匹配
   */
  def isMatch: Boolean = (this.hitState & Hit.MATCH) > 0

  /**
   *
   */
  def setMatch(): Unit = {
    this.hitState = this.hitState | Hit.MATCH
  }

  /**
   * 判断是否是词的前缀
   */
  def isPrefix: Boolean = (this.hitState & Hit.PREFIX) > 0

  /**
   *
   */
  def setPrefix(): Unit = {
    this.hitState = this.hitState | Hit.PREFIX
  }

  /**
   * 判断是否是不匹配
   */
  def isUnmatch: Boolean = this.hitState == Hit.UNMATCH

  /**
   *
   */
  def setUnmatch(): Unit = {
    this.hitState = Hit.UNMATCH
  }

  def getMatchedDictSegment: DictSegment = matchedDictSegment

  def setMatchedDictSegment(matchedDictSegment: DictSegment): Unit = {
    this.matchedDictSegment = matchedDictSegment
  }

  def getBegin: Int = begin

  def setBegin(begin: Int): Unit = {
    this.begin = begin
  }

  def getEnd: Int = end

  def setEnd(end: Int): Unit = {
    this.end = end
  }
}