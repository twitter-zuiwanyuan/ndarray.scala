package org.lasersonlab.zarr.untyped

import hammerlab.path._
import io.circe.Json
import org.lasersonlab.zarr
import org.lasersonlab.zarr.Format.`2`
import org.lasersonlab.zarr.dtype.DataType._
import org.lasersonlab.zarr.{ Suite, untyped }

class GroupTest
  extends Suite {
  test("load") {
    val path = Path("/Users/ryan/c/hdf5-experiments/files/L6_Microglia.ad.32m.zarr")

    val group @
      Group(
        arrays,
        groups,
        attrs,
        metadata
      ) =
      Group(path)
        .fold(
          fail(_),
          identity
        )

    arrays.keys should be(Set("X", "obs", "var"))
    groups.keys should be(Set("uns"))

    ==(attrs, None)
    ==(metadata, Group.Metadata(`2`))

    val `var` = group.array('var)
    `var`. shape should be(27998 :: Nil)
    `var`.chunks should be(27998 :: Nil)

    `var`.metadata should be(
      new zarr.Metadata(
         shape = Seq(27998),
        chunks = Seq(27998),
        dtype =
          struct(
            Vector(
              StructEntry("index", long),
              StructEntry("Accession", string(18)),
              StructEntry("Gene", short),
              StructEntry("_LogCV", double),
              StructEntry("_LogMean", double),
              StructEntry("_Selected", long),
              StructEntry("_Total", double),
              StructEntry("_Valid", long)
            )
          ),
        fill_value =
          Json.fromString(
            // 68 0-bytes, base64-encoded
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
          )
      )
    )
  }

  test("save") {
    // TODO
  }
}
