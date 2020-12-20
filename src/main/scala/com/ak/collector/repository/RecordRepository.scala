package com.ak.collector.repository

import java.util.UUID

import cats.effect.Sync
import cats.syntax.functor._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import fs2.Stream
import RecordRepository._

class RecordRepository[F[_]: Sync](transactor: Transactor[F]) {

  def create(record: RecordRequest): F[UUID] = sql"""
      INSERT INTO records (name, link, price, note) VALUES (${record.name}, ${record.link}, ${record.price}, ${record.note})
    """.update.withUniqueGeneratedKeys[UUID]("id").transact(transactor)

  def update(id: UUID, record: RecordRequest): F[Option[UUID]] = sql"""
      UPDATE records SET name = ${record.name}, link = ${record.link}, price = ${record.price}, note = ${record.note}
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
      DELETE FROM records WHERE id = $id
    """.update.run
    .transact(transactor)
    .map(affectedRows => {
      if (affectedRows == 1) {
        Some(())
      } else {
        None
      }
    })


  def getById(id: UUID): F[Option[Record]] = sql"""
      SELECT id, name, link, price, note FROM records WHERE id = $id
    """.query[Record].option.transact(transactor)

  def findByLink(link: String): F[Option[Record]] = sql"""
      SELECT id, name, link, price, note FROM records WHERE link = $link
    """.query[Record].option.transact(transactor)

  def all: Stream[F, Record] = sql"""
    SELECT id, name, link, price, note FROM records
  """.query[Record].stream.transact[F](transactor)
}

object RecordRepository {
  case class RecordRequest(name: String, link: String, price: Int, note: String)
  case class Record(id: UUID, name: String, link: String, price: Int, note: String)
}
