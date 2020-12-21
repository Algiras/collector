package com.ak.collector

import org.http4s.EntityEncoder
import org.http4s.circe._
import java.util.UUID
import io.circe.Encoder

package object service {
    implicit def listEncoder[F[_], A: Encoder]: EntityEncoder[F, List[A]] = jsonEncoderOf[F, List[A]]
    implicit def uuidEncoder[F[_]]: EntityEncoder[F, UUID]     = jsonEncoderOf[F, UUID]
    implicit def unitEncoder[F[_]]: EntityEncoder[F, Unit]     = jsonEncoderOf[F, Unit]
}
