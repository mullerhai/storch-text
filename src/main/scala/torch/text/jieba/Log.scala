package torch.text.jieba

object Log {
  private val LOG_ENABLE = System.getProperty("jieba.log.enable", "true").toBoolean

  def debug(debugInfo: String): Unit = {
    if (LOG_ENABLE) System.out.println(debugInfo)
  }

  def error(errorInfo: String): Unit = {
    if (LOG_ENABLE) System.err.println(errorInfo)
  }
}