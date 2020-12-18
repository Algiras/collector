package com.ak.collector.db

import cats.effect.{Async, Blocker, ContextShift, Resource, Sync}
import com.ak.collector.config.DatabaseConfig
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import org.flywaydb.core.Flyway

object Database {
  def transactor[F[_]: Sync: Async: ContextShift](
      config: DatabaseConfig
  ): Resource[F, HikariTransactor[F]] = {
    for {
      blocker <- Blocker[F]
      ce      <- ExecutionContexts.fixedThreadPool[F](config.threadPool)
      transactor <- HikariTransactor.newHikariTransactor[F](
        config.driver,
        config.url,
        config.user,
        config.password,
        ce,
        blocker
      )
    } yield transactor
  }

  def initialize[F[_]: Sync](transactor: HikariTransactor[F]): F[Unit] = {
    transactor.configure { dataSource =>
      Sync[F].delay {
        val flyWay = Flyway.configure().dataSource(dataSource).load()
        flyWay.migrate()
        ()
      }
    }
  }
}
