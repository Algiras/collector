package com.ak.collector.repository

import io.circe._
import cats.implicits._
import io.circe.parser._
import io.circe.syntax._
import ShapesDerivation._
import io.circe.generic.extras.semiauto.deriveConfiguredCodec
import doobie.util.meta.Meta

sealed trait CustomInput {
  def name: String;
  def value: String;
}

object CustomInput {
  implicit val customInputCodec: Codec[CustomInput] = deriveConfiguredCodec[CustomInput]

  implicit val customInputMeta: Meta[List[CustomInput]] = Meta[Array[Byte]].imap(bytes => {
    val json   = parse(new String(bytes)).valueOr(throw _)
    val result = json.as[List[CustomInput]].valueOr(throw _)

    result

  })(
    _.asJson.noSpaces.getBytes()
  )

  case class Input(name: String, value: String) extends CustomInput
}
