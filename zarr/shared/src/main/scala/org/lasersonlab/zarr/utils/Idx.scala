package org.lasersonlab.zarr.utils

import hammerlab.either._
import hammerlab.math.utils._
import io.circe.{ Decoder, Encoder }
import org.lasersonlab.ndarray.Arithmetic
import org.lasersonlab.ndarray.Arithmetic.Id

/**
 * Type-class abstracting over dimension-sizes / indices in [[lasersonlab.zarr.Array]]s
 *
 * [[Int]]s are the most common path, and [[Idx.Int]] is exposed implicitly in [[lasersonlab.zarr]]
 */
sealed trait Idx {
  type T

  implicit val arithmeticId : Arithmetic[T,   T]
  implicit val arithmeticInt: Arithmetic[T, Int]

  def int(t: T): CastException | Int

  implicit val encoder: Encoder[T]
  implicit val decoder: Decoder[T]
}
object Idx {
  sealed trait T[_T] extends Idx { type T = _T }
  object T {
    implicit def specify(implicit idx: Idx): Idx.T[idx.T] = idx match { case t: T[idx.T] ⇒ t }
  }

  implicit object Int
    extends T[scala.Int] {

    val arithmeticId : Id[Int] = Arithmetic.intint
    val arithmeticInt: Id[Int] = Arithmetic.intint

    def int(t: Int): CastException | Int = Right(t)

    val encoder = io.circe.Encoder.encodeInt
    val decoder = io.circe.Decoder.decodeInt
  }

  object Long
    extends T[scala.Long] {

    val arithmeticId : Arithmetic[Long, Long] = Arithmetic.longlong
    val arithmeticInt: Arithmetic[Long,  Int] = Arithmetic.longint

    def int(t: Long): CastException | Int = t.safeInt

    val encoder = io.circe.Encoder.encodeLong
    val decoder = io.circe.Decoder.decodeLong
  }

  trait syntax {
    implicit def unwrapEncoder(implicit idx: Idx): Encoder[idx.T] = idx.encoder
    implicit def unwrapDecoder(implicit idx: Idx): Decoder[idx.T] = idx.decoder
    implicit def unwrapArithmeticInt[T](implicit idx: Idx.T[T]): Arithmetic[T, Int] = idx.arithmeticInt
    @inline implicit def makeIdxOps[T](t: T): Ops[T] = Ops(t)
  }
  object syntax extends syntax

  implicit class Ops[T](val t: T) extends AnyVal {
    def int(implicit idx: Idx.T[T]): CastException | Int = idx.int(t)
  }
}
