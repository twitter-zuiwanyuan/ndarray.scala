package org.lasersonlab.zarr

import cats.{ Applicative, Eval, Foldable, Functor, Traverse }
import hammerlab.option
import hammerlab.option._
import hammerlab.path._
import io.circe.Decoder
import org.lasersonlab.ndarray.{ Arithmetic, ArrayLike, Bytes, ScanRight, Sum, ToArray }
import org.lasersonlab.zarr.FillValue.FillValueDecoder
import org.lasersonlab.zarr.dtype.DataType
import shapeless.Nat

/**
 * A Zarr N-dimensional array
 *
 * Storage of the ND-array of chunks, as well as the records in each chunk, are each a configurable type-param; see
 * companion-object for some convenient constructors
 */
trait Array[T] {
  type Shape
  type A[_]
  type Chunk[_]

  implicit def traverseA: Traverse[A]
  implicit def foldableChunk: Foldable[Chunk]

  def shape: Shape
  def chunkShape: Shape

  def apply(idx: Shape): T

  def metadata: Metadata[T, Shape]
  def chunks: A[Chunk[T]]
  def attrs: Opt[Attrs]
}

object Array {

  type S[S, T] = Array[T] { type Shape = S }

  type Aux[T, S, _A[_], _Chunk[_]] =
    Array[T] {
      type Shape = S
      type     A[U] =     _A[U]
      type Chunk[U] = _Chunk[U]
    }

  /**
   * Load an ND-array of chunks from a [[Path directory]]
   *
   * Each chunk lives in a file with basename given by the provided [[Key]] ('.'-joined indices)
   */
  def chunks[
        T: DataType.Aux,
    Shape: Arithmetic.Id,
     A[U]: Traverse
  ](
           dir: Path,
      arrShape: Shape,
    chunkShape: Shape
  )(
     implicit
     indices: Indices.Aux[A, Shape],
     ai: Arithmetic[Shape, Int],
     key: Key[Shape],
     scanRight: ScanRight.Aux[Shape, Int, Int, Shape],
     sum: Sum.Aux[Shape, Int],
     compressor: Compressor,
  ):
    Exception |
    A[Bytes.Aux[T, Shape]]
  = {

    val chunkRanges = (arrShape + chunkShape - 1) / chunkShape

    // We use Traverse and Applicative instances for Either, Traverse[A], and Functor syntax
    import cats.implicits._

    val chunks =
      indices(chunkRanges)
        .map {
            idx ⇒
              val start = idx * chunkShape
              val end = arrShape min ((idx + 1) * chunkShape)

              // chunks in the last "row" of any dimension may be smaller
              val shape = end - start

              Chunk(
                dir / key(idx),
                shape,
                idx,
                start,
                end,
                compressor
              )
          }

    // A[Err | Chunk] -> Err | A[Chunk]
    chunks
      .sequence[
        Exception | ?,
        Bytes.Aux[T, Shape]
      ]
  }

  /**
   * Convenience-constructor: given a data-type and a [[Nat (type-level) number of dimensions]], load an [[Array]] from
   * a [[Path directory]]
   *
   * Uses a [[VectorInts]] as evidence for mapping from the [[Nat]] to a concrete shape
   */
  def apply[
    T,
    N <: Nat
  ](
    dir: Path
  )(
    implicit
    v: VectorInts[N],
    d: Decoder[DataType.Aux[T]],
    dt: FillValueDecoder[T],
  ):
    Exception |
    Aux[T, v.Shape, v.A, Bytes.Aux[?, v.Shape]]
  = {
    import v._
    apply[T, v.Shape, v.A](dir)(
      // shouldn't have to do list all these explicitly: https://github.com/scala/bug/issues/11086
      d = d,
      ti = ti,
      traverse = traverse,
      arrayLike = arrayLike,
      ai = ai,
      scanRight = scanRight,
      sum = sum,
      dt = dt,
      arithmetic = arithmetic,
      key = key,
      ds = ds
    )
  }

  def apply[
    T,
    _Shape,
    _A[U]
  ](
    dir: Path
  )(
    implicit
    d: Decoder[DataType.Aux[T]],
    ti: Indices.Aux[_A, _Shape],
    traverse: Traverse[_A],
    arrayLike: ArrayLike.Aux[_A, _Shape],
    ai: Arithmetic[_Shape, Int],
    scanRight: ScanRight.Aux[_Shape, Int, Int, _Shape],
    sum: Sum.Aux[_Shape, Int],
    dt: FillValueDecoder[T],
    arithmetic: Arithmetic.Id[_Shape],
    key: Key[_Shape],
    ds: Decoder[_Shape],
  ):
    Exception |
    Aux[T, _Shape, _A, Bytes.Aux[?, _Shape]]
  =
    for {
      _metadata ← Metadata[T, _Shape](dir)
      _attrs ← Attrs(dir)
      _chunks ← {
        implicit val md = _metadata
        import Metadata._
        chunks[T, _Shape, _A](
          dir,
          md.shape,
          md.chunks
        )
      }
    } yield
      new Array[T] {
        type Shape = _Shape
        type A[U] = _A[U]
        type Chunk[U] = Bytes.Aux[U, Shape]

        override val traverseA = traverse
        override val foldableChunk = Bytes.foldableAux

        val metadata = _metadata
        val   chunks =   _chunks
        val    attrs =    _attrs

        val shape = metadata.shape
        val chunkShape = metadata.chunks

        def apply(idx: Shape): T =
          Bytes.arrayLike(
            arrayLike(
              chunks,
              idx / chunkShape
            ),
            idx % chunkShape
          )
      }

  //implicit val anyFoldable: Foldable[Array] = ???

  import cats.implicits._
  implicit def foldable[Shape]: Foldable[Array.S[Shape, ?]] =
    new Foldable[Array.S[Shape, ?]] {
      type F[A] = Array.S[Shape, A]
//      def traverse[G[_], A, B](fa: F[A])(f: A ⇒ G[B])(implicit ev: Applicative[G]): G[F[B]] = {
//        import fa._
//        implicit val funct: Traverse[fa.Chunk] = ???
//        chunks
//          .map {
//            chunk ⇒
//              chunk
//                .map(f)
//                .sequence
//          }
//          .sequence
//          .map {
//            _chunks ⇒
//              new Array[B, Shape] {
//                type A[U] = fa.A[U]
//                type Chunk[U] = fa.Chunk[U]
//                implicit def traverseA: Traverse[A] = fa.traverseA
//                implicit def foldableChunk: Foldable[Chunk] = fa.foldableChunk
//                val shape: Shape = fa.shape
//                val chunkShape: Shape = fa.chunkShape
//                def apply(idx: Shape): B = ???
//                def metadata: Metadata[B, Shape] = ???
//                val chunks = _chunks
//                def attrs: option.Opt[Attrs] = fa.attrs
//              }
//          }
//      }

      def foldLeft[A, B](fa: F[A], b: B)(f: (B, A) ⇒ B): B =
        fa
          .traverseA
          .foldLeft(
            fa.chunks,
            b
          ) {
            (b, chunk) ⇒
              fa
                .foldableChunk
                .foldLeft(
                  chunk,
                  b
                )(
                  f
                )
          }

      def foldRight[A, B](fa: F[A], lb: Eval[B])(f: (A, Eval[B]) ⇒ Eval[B]): Eval[B] =
        fa
          .traverseA
          .foldRight(
            fa.chunks,
            lb
          ) {
            (chunk, lb) ⇒
              fa
                .foldableChunk
                .foldRight(
                  chunk,
                  lb
                )(
                  f
                )
          }
    }
}
