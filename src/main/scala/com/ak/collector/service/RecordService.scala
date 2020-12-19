package com.ak.collector.service

import java.util.UUID

import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.ak.collector.models.User
import com.ak.collector.repository.RecordRepository
import com.ak.collector.repository.RecordRepository._
import io.circe.generic.auto._
import org.http4s.{AuthedRoutes, EntityDecoder, EntityEncoder}
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import fs2._
import org.http4s.MediaType
import org.http4s.Charset
import org.http4s.headers.`Content-Type`

class RecordService[F[_]: Sync](repository: RecordRepository[F]) extends Http4sDsl[F] {
  implicit val recordEncoder: EntityEncoder[F, Record] = jsonEncoderOf[F, Record]
  implicit val uuidEncoder: EntityEncoder[F, UUID]     = jsonEncoderOf[F, UUID]
  implicit val unitEncoder: EntityEncoder[F, Unit]     = jsonEncoderOf[F, Unit]

  implicit val recordRequestDecoder: EntityDecoder[F, RecordRequest] = jsonOf[F, RecordRequest]

  val routes: AuthedRoutes[User, F] = AuthedRoutes.of[User, F] {
    case GET -> Root / UUIDVar(id) as _ =>
      for {
        recordOpt <- repository.getById(id)
        response  <- result(recordOpt)
      } yield response
    case GET -> Root / "byLink" :? LinkQueryParamMatcher(link) as _ =>
      for {
        recordOpt <- repository.findByLink(link)
        response  <- result(recordOpt)
      } yield response
    case GET -> Root / "data.csv" as _ =>
      Ok(
        (Stream[F, String]("id\tlink\tname\tprice") ++ repository.all.map(
          record => s"${record.id}\t${record.link}\t${record.name}\t${record.price}")
        ).intersperse("\n").through(text.utf8Encode)
      ).map(_.withContentType(`Content-Type`(MediaType.text.csv, Some(Charset.`UTF-8`))))
    case usrReq @ POST -> Root as _ =>
      for {
        reqBody  <- usrReq.req.as[RecordRequest]
        id       <- repository.create(reqBody)
        response <- Ok(id.toString())
      } yield response
    case usrReq @ PUT -> Root / UUIDVar(id) as _ =>
      for {
        reqBody   <- usrReq.req.as[RecordRequest]
        recordOpt <- repository.update(id, reqBody)
        response  <- result(recordOpt.map(_.toString()))
      } yield response
    case DELETE -> Root / UUIDVar(id) as _ =>
      for {
        recordOpt <- repository.getById(id)
        response <- recordOpt match {
          case Some(resp) => repository.delete(resp.id).flatMap(result(_))
          case None       => NotFound()
        }
      } yield response
  }

  private def result[T](from: Option[T])(implicit enc: EntityEncoder[F, T]) = from match {
    case Some(record) => Ok(record)
    case None         => NotFound()
  }

  object LinkQueryParamMatcher extends QueryParamDecoderMatcher[String]("link")
}
