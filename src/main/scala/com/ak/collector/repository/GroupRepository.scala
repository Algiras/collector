package com.ak.collector.repository

import java.util.UUID

import cats.effect.Sync
import cats.syntax.functor._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import fs2.Stream
import com.ak.collector.repository.GroupRepository.GroupRequest
import com.ak.collector.repository.GroupRepository.Group
import com.ak.collector.repository.CustomInput._
import UserRepository.User

trait GroupStore[F[_]] {
  def create(group: GroupRequest): F[UUID]
  def update(id: UUID, group: GroupRequest): F[Option[UUID]]
  def delete(id: UUID): F[Option[Unit]]
  def getById(id: UUID): F[Option[Group]]
  def all: Stream[F, Group]
}

class GroupRepository[F[_]: Sync](transactor: Transactor[F]) {
  def store(user: User) = new GroupStore[F] {
    def create(group: GroupRequest): F[UUID] = sql"""
      INSERT INTO groups (name, custom_inputs, author) VALUES (${group.name}, ${group.customInputs}, ${user.id})
    """.update.withUniqueGeneratedKeys[UUID]("id").transact(transactor)

    def update(id: UUID, group: GroupRequest): F[Option[UUID]] = sql"""
      UPDATE groups SET name = ${group.name}, custom_inputs = ${group.customInputs}
      WHERE id = $id AND (author = ${user.id} OR author IS NULL)
    """.update.run
      .transact(transactor)
      .map(affectedRows => {
        if (affectedRows == 1) {
          Some(id)
        } else {
          None
        }
      })

    def delete(id: UUID): F[Option[Unit]] = sql"""
      DELETE FROM groups WHERE id = $id AND (author = ${user.id} OR author IS NULL)
    """.update.run
      .transact(transactor)
      .map(affectedRows => {
        if (affectedRows == 1) {
          Some(())
        } else {
          None
        }
      })

    def getById(id: UUID): F[Option[Group]] = sql"""
      SELECT id, name, custom_inputs FROM groups WHERE id = $id AND (author = ${user.id} OR author IS NULL)
    """.query[Group].option.transact(transactor)

    def all: Stream[F, Group] = sql"""
    SELECT id, name, custom_inputs FROM groups WHERE (author = ${user.id} OR author IS NULL)
  """.query[Group].stream.transact[F](transactor)
  }
}

object GroupRepository {
  case class GroupRequest(name: String, customInputs: List[CustomInput])
  case class Group(id: UUID, name: String, customInputs: List[CustomInput])
}
