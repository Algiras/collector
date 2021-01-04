package com.ak.collector.service

import java.util.concurrent.TimeUnit

import cats.data.{Kleisli, OptionT}
import cats.effect.{Sync, Timer}
import cats.implicits._
import com.ak.collector.config.Auth
import io.circe.generic.auto._
import org.http4s._
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.dsl.Http4sDsl
import org.http4s.server.AuthMiddleware
import org.reactormonk.{CryptoBits, PrivateKey}
import java.util.UUID
import com.ak.collector.repository.UserRepository
import com.ak.collector.repository.UserRepository._

class AuthService[F[_]: Sync: Timer](authKey: String, userRepository: UserRepository[F])
    extends Http4sDsl[F] {

  implicit val recordRequestDecoder: EntityDecoder[F, LoginRequest] = jsonOf[F, LoginRequest]
  implicit val userRequestDecoder: EntityDecoder[F, UserRequest]    = jsonOf[F, UserRequest]

  implicit val userDecoder: EntityEncoder[F, User]                  = jsonEncoderOf[F, User]

  private val logInRequest: Kleisli[F, Request[F], Response[F]] = Kleisli({ request =>
    for {
      time <- Timer[F].clock.realTime(TimeUnit.MILLISECONDS)
      resp <- verifyLogin(request: Request[F]).flatMap {
        case Left(error) =>
          Forbidden(error)
        case Right(user) =>
          val message = crypto.signToken(user.id.toString, time.toString)
          Ok(user)
            .map(_.addCookie(ResponseCookie("authcookie", message)))
      }
    } yield resp
  })

  private def verifyLogin(request: Request[F]): F[Either[String, User]] = for {
    requestMsg <- request.as[LoginRequest]
    isValid    <- userRepository.check(requestMsg.email, requestMsg.password)
    res <-
      if (isValid) {
        userRepository.getByEmail(requestMsg.email).map {
          case Some(res) => Right(res)
          case None      => Left("User not found")
        }
      } else {
        Sync[F].pure(Either.left[String, User]("User not found"))
      }
  } yield res

  private val retrieveUser: Kleisli[F, UUID, User] = Kleisli((id: UUID) =>
    userRepository.getById(id).flatMap {
      case Some(user) => Sync[F].pure(user)
      case None       => Sync[F].raiseError(new RuntimeException("User does not exist"))
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

  private val meRoute = AuthedRoutes.of[User, F] {
    case GET -> Root as user => Ok(user)
    case req @ POST -> Root / "user" / "create" as user =>
      for {
        body <- req.req.as[UserRequest]
        resp <- if(user.role == Role.Admin) {
          userRepository.create(body).flatMap(Ok(_))
        } else Sync[F].pure(Response[F](Unauthorized))
      } yield resp
  }

  private val adminRoute = AuthedRoutes.of[User, F] {
    case req @ POST -> Root / "user" / "create" as user =>
      for {
        body <- req.req.as[UserRequest]
        resp <- if(user.role == Role.Admin) {
          userRepository.create(body).flatMap(Ok(_))
        } else Sync[F].pure(Response[F](Unauthorized))
      } yield resp
  }

  private val onFailure: AuthedRoutes[String, F] =
    Kleisli(req => OptionT.liftF[F, Response[F]](Forbidden(req.context)))

  val key            = PrivateKey(scala.io.Codec.toUTF8(authKey))
  val crypto         = CryptoBits(key)
  val authMiddleware = AuthMiddleware(authUser, onFailure)
  val loginRoutes: HttpRoutes[F] =
    HttpRoutes((req: Request[F]) => OptionT.liftF(logInRequest.run(req)))
  val meRoutes = authMiddleware(meRoute)
  val adminRoutes = authMiddleware(adminRoute)

  case class LoginRequest(email: String, password: String)
}

object AuthService {
  def apply[F[_]: Sync: Timer](auth: Auth, userRepository: UserRepository[F]): F[AuthService[F]] = {
    val service = new AuthService[F](auth.key, userRepository)
    val user    = auth.user;

    for {
      _ <- Sync[F].ifM(userRepository.check(auth.user.email, auth.user.password))(
        Sync[F].unit,
        userRepository.create(UserRequest(user.email, user.password, Role.Admin)).map(_ => ())
      )
    } yield service

  }
}
