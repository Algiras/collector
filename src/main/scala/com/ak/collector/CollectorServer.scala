package com.ak.collector

import cats.effect.{Blocker, ConcurrentEffect, ContextShift, Resource, Timer}
import com.ak.collector.config.ServiceConfig
import com.ak.collector.db.Database
import com.ak.collector.repository.RecordRepository
import com.ak.collector.service.{AuthService, RecordService}
import doobie.hikari.HikariTransactor
import fs2.Stream
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger

import scala.concurrent.ExecutionContext.global
import com.ak.collector.repository.GroupRepository
import com.ak.collector.service.GroupService
import com.ak.collector.repository.UserRepository

object CollectorServer {
  private case class Runtime[F[_]](config: ServiceConfig, transactor: HikariTransactor[F])

  private def setupRuntime[F[_]: ConcurrentEffect: ContextShift]: Resource[F, Runtime[F]] = for {
    blocker    <- Blocker[F]
    config     <- Resource.liftF(config.ServiceConfig.load[F](blocker))
    transactor <- Database.transactor[F](config.database)
    _          <- Resource.liftF(Database.initialize(transactor))
  } yield Runtime(config, transactor)

  def stream[F[_]: ConcurrentEffect: ContextShift: Timer]: Stream[F, Nothing] = {
    for {
      runtime <- Stream.resource(setupRuntime[F])
      recordRepository = new RecordRepository[F](runtime.transactor)
      groupRepository  = new GroupRepository[F](runtime.transactor)
      userRepository = new UserRepository[F](runtime.transactor)

      recordService = new RecordService[F](recordRepository.store(_))
      groupService  = new GroupService[F](groupRepository.store(_))
      authService  <- Stream.eval(AuthService[F](runtime.config.auth, userRepository))

      httpApp = Router(
        "/records" -> authService.authMiddleware(recordService.routes),
        "/groups"  -> authService.authMiddleware(groupService.routes),
        "/admin"      -> authService.adminRoutes,
        "/login"   -> authService.loginRoutes,
        "/me"      -> authService.meRoutes,
      ).orNotFound

      finalHttpApp = Logger.httpApp(logHeaders = true, logBody = true)(httpApp)

      exitCode <- BlazeServerBuilder[F](global)
        .bindHttp(runtime.config.server.port, runtime.config.server.host)
        .withHttpApp(finalHttpApp)
        .serve
    } yield exitCode
  }.drain
}
