package models

import play.api.libs.json.{JsObject, JsSuccess, _}

package object json {

  /** Extensions to [[play.api.libs.json.JsPath]].
   *
   * @param path The extended path object
   */
  implicit class JsPathExtensions(path: JsPath) {

    /** Read a value only if it's equal to the given value. */
    def readIfEquals[T](t: T)(implicit r: Reads[T]): Reads[T] =
      path.read[T](Reads.filter[T](JsonValidationError("validate.error.incorrectType", t))(_ == t))

    /** Attempt to read a list of T, falling back to a single T. */
    def readSeqOrSingle[T](implicit r: Reads[T]): Reads[Seq[T]] =
      path.read[Seq[T]].orElse(path.readNullable[T].map(_.toSeq))

    /** Lazy variant of [[models.json.JsPathExtensions.readSeqOrSingle]]. */
    def lazyReadSeqOrSingle[T](r: => Reads[T]): Reads[Seq[T]] =
      Reads(js => readSeqOrSingle(r).reads(js))

    /** Nullable variant of [[models.json.JsPathExtensions.readSeqOrSingle]]. */
    def readSeqOrSingleNullable[T](implicit r: Reads[T]): Reads[Option[Seq[T]]] =
      path.readNullable[Seq[T]].orElse(path.readNullable[T].map(s => s.map(Seq(_))))

    /** Lazy variant of [[models.json.JsPathExtensions.readSeqOrSingleNullable]]. */
    def lazyReadSeqOrSingleNullable[T](r: => Reads[T]): Reads[Option[Seq[T]]] =
      Reads(js => readSeqOrSingleNullable(r).reads(js))

    /** Attempt to read a path, falling back on a default value. */
    def readWithDefault[T](t: T)(implicit r: Reads[T]): Reads[T] =
      path.read[T].orElse(Reads.pure(t))

    /** Attempt to read a path, falling back on a default value. */
    def readNullableWithDefault[T](t: T)(implicit r: Reads[T]): Reads[Option[T]] =
      path.readNullable[T].orElse(Reads.pure(Some(t))).map(_.orElse(Some(t)))

    /** Attempt to read a list, falling back on an empty list if the
     * path does not exist.
     */
    def readSeqOrEmpty[T](implicit r: Reads[T]): Reads[Seq[T]] = Reads[Seq[T]] { json =>
      path.asSingleJsResult(json).fold(
        err => JsSuccess[Seq[T]](Seq.empty[T], path),
        valid => valid.validate[Seq[T]](Reads.seq(r))
      )
    }

    /** Read the first item from a list that may be none, if
      * the path is missing.
      */
    def readHeadNullable[T](implicit r: Reads[T]): Reads[Option[T]] =
      readSeqOrEmpty(r).map(_.headOption)

    /** Read a single item or the head of a sequence. */
    def readHeadOrSingleNullable[T](implicit r: Reads[T]): Reads[Option[T]] =
      path.read[Seq[T]].map(_.headOption).orElse(path.readNullable[T])

    /** Lazy variant of [[models.json.JsPathExtensions.readHeadNullable]]. */
    def lazyReadHeadNullable[T](r: => Reads[T]): Reads[Option[T]] =
      Reads(js => readHeadNullable(r).reads(js))

    /** Attempt to read a path, falling back on a default value. */
    def formatWithDefault[T](t: T)(implicit fmt: Format[T]): OFormat[T] =
      OFormat(readWithDefault(t), path.write[T])

    /** Read/write a nullable item, with a default read value (and a nullable write) */
    def formatNullableWithDefault[T: Format](t: T): OFormat[Option[T]] =
      OFormat(readNullableWithDefault(t), path.writeNullable[T])

    /** Read/write an item, but validating on read that it's equal to the given value  */
    def formatIfEquals[T](v: T)(implicit fmt: Format[T]): OFormat[T] =
      OFormat[T](readIfEquals(v), path.write[T])

    /** Write a list if it is non-empty, otherwise nothing. */
    def writeSeqOrEmpty[T](implicit w: Writes[T]): OWrites[Seq[T]] = OWrites { o =>
      if (o.isEmpty) Json.obj() else path.write[Seq[T]].writes(o)
    }

    def lazyWriteSeqOrEmpty[T](w: => Writes[T]): OWrites[Seq[T]] = OWrites { o =>
      if (o.isEmpty) Json.obj() else path.write(Writes.seq(w)).writes(o)
    }

    /** Write a list if it is non-empty, otherwise nothing. */
    def writeNullableSeqOrEmpty[T](implicit w: Writes[T]): OWrites[Option[Seq[T]]] = OWrites { o =>
      if (o.toSeq.flatten.isEmpty) Json.obj() else path.write[Seq[T]].writes(o.toSeq.flatten)
    }

    def formatSeqOrEmpty[T](implicit fmt: Format[T]): OFormat[Seq[T]] =
      OFormat[Seq[T]](readSeqOrEmpty(fmt), writeSeqOrEmpty(fmt))

    def lazyReadSeqOrEmpty[T](r: => Reads[T]): Reads[Seq[T]] =
      Reads(js => readSeqOrEmpty(r).reads(js))

    def formatSeqOrSingle[T](implicit fmt: Format[T]): OFormat[Seq[T]] =
      OFormat(readSeqOrSingle(fmt), writeSeqOrEmpty(fmt))

    def formatSeqOrSingleNullable[T](implicit fmt: Format[T]): OFormat[Option[Seq[T]]] =
      OFormat(readSeqOrSingleNullable(fmt), writeNullableSeqOrEmpty(fmt))

    def formatHeadOrSingleNullable[T](implicit fmt: Format[T]): OFormat[Option[T]] =
      OFormat(readHeadOrSingleNullable(fmt), path.writeNullable[T])

    def lazyFormatSeqOrEmpty[T](fmt: => Format[T]): OFormat[Seq[T]] =
      OFormat[Seq[T]](lazyReadSeqOrEmpty(fmt), lazyWriteSeqOrEmpty(fmt))

    def lazyNullableSeqWrites[T](w: => Writes[T]): OWrites[Seq[T]] =
      OWrites((t: Seq[T]) => writeSeqOrEmpty[T](w).writes(t).as[JsObject])

    def lazyNullableSeqFormat[T](fmt: => Format[T]): OFormat[Seq[T]] =
      OFormat[Seq[T]](lazyReadSeqOrEmpty(fmt), lazyNullableSeqWrites(fmt))
  }
}
