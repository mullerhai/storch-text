package torch.text.vocab

import torch.{BFloat16, ComplexNN, Default, Float32, FloatNN, Tensor, nn}
import torch.nn.modules.TensorModule
import torch.nn.modules.{HasParams, Module}
import torch.internal.NativeConverters.{fromNative, toNative}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.Random


class Vocab(tokens: Seq[String]) {
  private val counter = mutable.Map[String, Int]()
  tokens.foreach(token => counter(token) = counter.getOrElse(token, 0) + 1)
  private val sortedTokens = counter.toSeq.sortBy(-_._2).map(_._1)
  val tokenToIdx = mutable.Map[String, Int]()
  val idxToToken = mutable.Map[Int, String]()
  sortedTokens.zipWithIndex.foreach { case (token, idx) =>
    tokenToIdx(token) = idx
    idxToToken(idx) = token
  }

  def len(): Int = tokenToIdx.size

  def getItem(tokens: Seq[String]): Seq[Int] = tokens.map(tokenToIdx)

  def to_tokens(indices: Seq[Int]): Seq[String] = indices.map(idxToToken)
}

//class SkipGram[ParamType <: FloatNN | ComplexNN: Default](vocabSize: Int, embedSize: Int) extends TensorModule{
//  val inputEmbedding = register(nn.Embedding(vocabSize, embedSize))
//  val outputEmbedding = register(nn.Embedding(vocabSize, embedSize))
//
//  def apply(center: Tensor[Float32], contextsAndNegatives: Tensor[Float32]): Tensor[Float32] = {
//    val v = inputEmbedding(center)
//    val u = outputEmbedding(contextsAndNegatives)
//    val pred = torch.bmm(v.unsqueeze(1), u.permute(0, 2, 1)).squeeze(1)
//    pred
//  }
//
//  override def apply(v1: Tensor[ParamType]): Tensor[ParamType] = ???
//}