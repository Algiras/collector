package com.ak.collector.config

import cats.effect.{Blocker, ContextShift, Sync}
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import pureconfig.module.catseffect.syntax._
import com.ak.collector.repository.UserRepository
import pureconfig.ConfigReader
import com.ak.collector.repository.UserRepository.Role

case class User(email: String, password: String, role: UserRepository.Role)

case class ServerConfig(host: String, port: Int)
case class Auth(user: User, key: String)
case class DatabaseConfig(
    driver: String,
    url: String,
    user: String,
    password: String,
    threadPool: Int
)
case class ServiceConfig(server: ServerConfig, database: DatabaseConfig, auth: Auth)

object ServiceConfig {
  implicit val roleReader = ConfigReader.fromNonEmptyStringOpt[Role]{
    case "admin" => Some(Role.Admin)
    case "regular" => Some(Role.Regular)
    case _ => None
  }

  def load[F[_]: Sync: ContextShift](blocker: Blocker): F[ServiceConfig] = {
    ConfigSource.default.loadF[F, ServiceConfig](blocker)
  }
}
