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

class GroupRepository[F[_]: Sync](transactor: Transactor[F]) {

  def create(group: GroupRequest): F[UUID] = sql"""
      INSERT INTO groups (name) VALUES (${group.name})
    """.update.withUniqueGeneratedKeys[UUID]("id").transact(transactor)

  def update(id: UUID, group: GroupRequest): F[Option[UUID]] = sql"""
      UPDATE groups SET name = ${group.name}
      WHERE id = $id
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
      DELETE FROM groups WHERE id = $id
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
      SELECT id, name FROM groups WHERE id = $id
    """.query[Group].option.transact(transactor)

  def all: Stream[F, Group] = sql"""
    SELECT id, name FROM groups
  """.query[Group].stream.transact[F](transactor)
}

object GroupRepository {
  case class GroupRequest(name: String)
  case class Group(id: UUID, name: String)
}
