package com.ak.collector.service

import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.ak.collector.repository.UserRepository.User
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
import com.ak.collector.repository.CustomInput

class RecordService[F[_]: Sync](repository: RecordRepository[F]) extends Http4sDsl[F] {
  implicit val customInputEncoder: EntityEncoder[F, CustomInput] = jsonEncoderOf[F, CustomInput]
  implicit val customInputDecoder: EntityDecoder[F, CustomInput] = jsonOf[F, CustomInput]
  implicit val recordEncoder: EntityEncoder[F, Record] = jsonEncoderOf[F, Record]
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
    case GET -> Root / UUIDVar(groupId) / "data.csv" as _ => for {
      headers <- repository.recordsHeadersByGroup(groupId)
      resp <- Ok(
        (Stream[F, String](headers.mkString("\t")) ++ repository.recordsByGroup(groupId).map(
          record => {
            val customInputMap: Map[String, String] = record.customInputs.foldLeft(List.empty[(String, String)])(
              (values, field) => (field.name -> field.value) :: values
            ).toMap

            headers.map(value => value match {
              case "id" => s"${record.id}"
              case "link" => record.link
              case "name" => record.name
              case "price" => s"${record.price}"
              case "note" => record.note
              case other => customInputMap.get(other).getOrElse("")
            }).map(txt => txt.replace("\n", ", ")).mkString("\t")
          })
        ).intersperse("\n").through(text.utf8Encode)
      ).map(_.withContentType(`Content-Type`(MediaType.text.csv, Some(Charset.`UTF-8`))))
    } yield  resp
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
