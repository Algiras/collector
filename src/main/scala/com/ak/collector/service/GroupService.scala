package com.ak.collector.service

import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.circe.generic.auto._
import org.http4s.{AuthedRoutes, EntityDecoder, EntityEncoder}
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import com.ak.collector.repository.GroupRepository
import com.ak.collector.repository.GroupRepository.Group
import com.ak.collector.repository.GroupRepository.GroupRequest
import com.ak.collector.repository.UserRepository.User

class GroupService[F[_]: Sync](repository: GroupRepository[F]) extends Http4sDsl[F] {
  implicit val groupEncoder: EntityEncoder[F, Group] = jsonEncoderOf[F, Group]
  implicit val groupRequestDecoder: EntityDecoder[F, GroupRequest] = jsonOf[F, GroupRequest]

  val routes: AuthedRoutes[User, F] = AuthedRoutes.of[User, F] {
    case GET -> Root / UUIDVar(id) as _ =>
      for {
        recordOpt <- repository.getById(id)
        response  <- result(recordOpt)
      } yield response
    case GET -> Root as _ => Ok(repository.all.compile.toList)
    case usrReq @ POST -> Root as _ =>
      for {
        reqBody  <- usrReq.req.as[GroupRequest]
        id       <- repository.create(reqBody)
        response <- Ok(id.toString())
      } yield response
    case usrReq @ PUT -> Root / UUIDVar(id) as _ =>
      for {
        reqBody   <- usrReq.req.as[GroupRequest]
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
