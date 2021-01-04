package com.ak.collector.repository

import cats.effect.Sync
import doobie.util.transactor.Transactor
import com.ak.collector.repository.UserRepository._
import java.util.UUID
import cats.syntax.functor._
import cats.syntax.flatMap._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import com.github.t3hnar.bcrypt._
import com.ak.collector.repository.UserRepository.Role.Admin
import com.ak.collector.repository.UserRepository.Role.Regular

class UserRepository[F[_]: Sync](transactor: Transactor[F]) {
  implicit val userRoleMeta: Meta[Role] = Meta[String].timap[Role](str =>
    str match {
      case "admin"   => Role.Admin
      case "regular" => Role.Regular
    }
  )((role: Role) =>
    role match {
      case Admin   => "admin"
      case Regular => "regular"
    }
  )

  case class UserDB(id: UUID, email: String, passwordHash: String, role: Role)

  def create(user: UserRequest): F[UUID] = for {
    password <- Sync[F].fromTry(user.password.bcryptSafe(12))
    id       <- sql"""
      INSERT INTO users (email, pass_hash, access_role) VALUES (${user.email}, ${password}, ${user.role})
    """.update.withUniqueGeneratedKeys[UUID]("id").transact(transactor)
  } yield id

  def check(email: String, password: String): F[Boolean] = for {
    passHash <- sql"SELECT pass_hash FROM users WHERE email = $email"
      .query[String]
      .option
      .transact(transactor)
    resp <- passHash match {
      case Some(hash) => Sync[F].fromTry(password.isBcryptedSafe(hash))
      case None       => Sync[F].pure(false)
    }
  } yield resp

  def getById(id: UUID): F[Option[User]] = sql"""
      SELECT id, email, access_role FROM users WHERE id = $id
    """.query[User].option.transact(transactor)

  def getByEmail(email: String): F[Option[User]] = sql"""
      SELECT id, email, access_role FROM users WHERE email = $email
    """.query[User].option.transact(transactor)

  def delete(id: UUID): F[Option[Unit]] = sql"""
      DELETE FROM users WHERE id = $id
    """.update.run
    .transact(transactor)
    .map(affectedRows => {
      if (affectedRows == 1) {
        Some(())
      } else {
        None
      }
    })
}

object UserRepository {
  sealed trait Role

  object Role {
    case object Admin   extends Role
    case object Regular extends Role
  }

  case class User(id: UUID, email: String, role: Role)
  case class UserRequest(email: String, password: String, role: Role)
}
