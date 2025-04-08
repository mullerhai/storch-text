package torch.text.jieba

import java.util.regex.Pattern

object CharacterUtil {
  private val connectors = Array[Char]('+', '#', '&', '.', '_', '-')
  var reSkip: Pattern = Pattern.compile("(\\d+\\.\\d+|[a-zA-Z0-9]+)")

  def ccFind(ch: Char): Boolean = {
    if (isChineseLetter(ch)) return true
    if (isEnglishLetter(ch)) return true
    if (isDigit(ch)) return true
    if (isConnector(ch)) return true
    false
  }

  def isChineseLetter(ch: Char): Boolean = {
    if (ch >= 0x4E00 && ch <= 0x9FA5) return true
    false
  }

  def isEnglishLetter(ch: Char): Boolean = {
    if ((ch >= 0x0041 && ch <= 0x005A) || (ch >= 0x0061 && ch <= 0x007A)) return true
    false
  }

  def isDigit(ch: Char): Boolean = {
    if (ch >= 0x0030 && ch <= 0x0039) return true
    false
  }

  def isConnector(ch: Char): Boolean = {
    for (connector <- connectors) {
      if (ch == connector) return true
    }
    false
  }

  /**
   * 全角 to 半角,大写 to 小写
   *
   * @param input
   * 输入字符
   * @return 转换后的字符
   */
  def regularize(input: Char): Char = {
    if (input == 12288) return 32
    else if (input > 65280 && input < 65375) return (input - 65248).toChar
    else if (input >= 'A' && input <= 'Z') return (input + 32).toChar
    input
  }
}