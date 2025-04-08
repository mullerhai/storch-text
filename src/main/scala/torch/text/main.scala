package torch.text

import torch.text.tokenizer.TFIDFAnalyzer

@main
def main(): Unit =
  val content = "孩子上了幼儿园 安全防拐教育要做好 I love china girls and boys"
  val topN = 5
  val tfidfAnalyzer = new TFIDFAnalyzer
  val list = tfidfAnalyzer.analyze(content, topN)
  for (word <- list) {
    println(word.getName + ":" + word.getTfidfvalue + ",")
  }
  println("Hello world!")

