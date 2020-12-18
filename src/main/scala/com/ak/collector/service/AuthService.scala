package com.ak.collector.service

import java.util.concurrent.TimeUnit

import cats.data.{Kleisli, OptionT}
import cats.effect.{Sync, Timer}
import cats.implicits._
import com.ak.collector.config.Auth
import com.ak.collector.models.User
import io.circe.generic.auto._
import org.http4s._
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.dsl.Http4sDsl
import org.http4s.server.AuthMiddleware
import org.reactormonk.{CryptoBits, PrivateKey}
import java.util.UUID

class AuthService[F[_]: Sync: Timer](auth: Auth) extends Http4sDsl[F] {
  implicit val recordRequestDecoder: EntityDecoder[F, LoginRequest] = jsonOf[F, LoginRequest]
  implicit val userDecoder: EntityEncoder[F, User]                  = jsonEncoderOf[F, User]

  private val logInRequest: Kleisli[F, Request[F], Response[F]] = Kleisli({ request =>
    for {
      time <- Timer[F].clock.realTime(TimeUnit.MILLISECONDS)
      resp <- verifyLogin(request: Request[F]).flatMap {
        case Left(error) =>
          Forbidden(error)
        case Right(user) =>
          val message = crypto.signToken(user.id.toString, time.toString)
          Ok(user.copy(password = "<REDACTED>")).map(_.addCookie(ResponseCookie("authcookie", message)))
      }
    } yield resp
  })

  private def verifyLogin(request: Request[F]): F[Either[String, User]] = for {
    requestMsg <- request.as[LoginRequest]
  } yield {
    if (requestMsg.email == auth.user.email && requestMsg.password == auth.user.password) {
      Right(auth.user)
    } else Left("User not found")
  }

  private val retrieveUser: Kleisli[F, UUID, User] = Kleisli((id: UUID) =>
    if (auth.user.id == id) {
      Sync[F].delay(auth.user)
    } else {
      Sync[F].raiseError(new RuntimeException("User does not exist"))
    }
  )

  private val authUser: Kleisli[F, Request[F], Either[String, User]] = Kleisli({ request =>
    val message = for {
      header <- headers.Cookie.from(request.headers).toRight("Cookie parsing error")
      cookie <- header.values.toList
        .find(_.name == "authcookie")
        .toRight("Couldn't find the authcookie")
      token   <- crypto.validateSignedToken(cookie.content).toRight("Cookie invalid")
      message <- Either.catchOnly[NumberFormatException](UUID.fromString(token)).leftMap(_.toString)
    } yield message
    message.traverse(retrieveUser.run)
  })

  private val meRoute = AuthedRoutes.of[User, F] { case GET -> Root as user =>
    Ok(user.copy(password = "<REDACTED>"))
  }

  private val onFailure: AuthedRoutes[String, F] =
    Kleisli(req => OptionT.liftF[F, Response[F]](Forbidden(req.context)))

  val key            = PrivateKey(scala.io.Codec.toUTF8(auth.key))
  val crypto         = CryptoBits(key)
  val authMiddleware = AuthMiddleware(authUser, onFailure)
  val loginRoutes: HttpRoutes[F] =
    HttpRoutes((req: Request[F]) => OptionT.liftF(logInRequest.run(req)))
  val meRoutes = authMiddleware(meRoute)

  case class LoginRequest(email: String, password: String)
}
