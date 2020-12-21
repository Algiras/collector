package com.ak.collector.repository

import java.util.UUID

import cats.effect.Sync
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import fs2.Stream
import RecordRepository._
import cats.implicits._

class RecordRepository[F[_]: Sync](transactor: Transactor[F]) {

  def create(record: RecordRequest) = (for {
    recordId <- sql"""
      INSERT INTO records (name, link, price, note) VALUES (${record.name}, ${record.link}, ${record.price}, ${record.note})
    """.update.withUniqueGeneratedKeys[UUID]("id")
    _        <- record.groups.map(RecordGroupRepository.create(recordId, _)).sequence_
  } yield recordId).transact(transactor)

  private def updateRecordGroup(currentRecord: Record, record: RecordRequest) = {
    val add = (record.groups.toSet -- currentRecord.groups.toSet)
      .map(RecordGroupRepository.create(currentRecord.id, _))
    val remove = (currentRecord.groups.toSet -- record.groups.toSet)
      .map(RecordGroupRepository.delete(currentRecord.id, _))

    (add ++ remove).toList.sequence_
  }

  def update(id: UUID, record: RecordRequest): F[Option[UUID]] = (for {
    currentRecord <- getByIdOp(id).map(_.get)
    update <- sql"""
      UPDATE records SET name = ${record.name}, link = ${record.link}, price = ${record.price}, note = ${record.note}
      WHERE id = $id
    """.update.run
      .map(affectedRows => {
        if (affectedRows == 1) {
          Some(id)
        } else {
          None
        }
      })
    _ <- updateRecordGroup(currentRecord, record)
  } yield update)
    .transact(transactor)

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

  def getByIdOp(id: UUID) =
    (
      sql"""
      SELECT id, name, link, price, note FROM records WHERE id = $id
    """.query[DBRecord].option,
      RecordGroupRepository.selectGroupsByRecordId(id)
    ).mapN((record, groups) => record.map(Record(_, groups)))

  def getById(id: UUID): F[Option[Record]] = getByIdOp(id).transact(transactor)

  def findByLink(link: String): F[Option[Record]] = (for {
    resultOpt <- sql"""
      SELECT id, name, link, price, note FROM records WHERE link = $link
    """.query[DBRecord].option
    groups <- resultOpt match {
      case Some(result) => RecordGroupRepository.selectGroupsByRecordId(result.id)
      case None         => List.empty[UUID].pure[ConnectionIO]
    }
  } yield resultOpt.map(Record(_, groups)))
    .transact(transactor)

  def recordsByGroup(groupId: UUID): Stream[F, DBRecord] = sql"""
    SELECT id, name, link, price, note FROM records JOIN record_group ON records.id = record_group.record_id
      WHERE record_group.group_id = ${groupId}
  """.query[DBRecord].stream.transact[F](transactor)
}

object RecordRepository {
  case class RecordRequest(name: String, link: String, price: Int, note: String, groups: List[UUID])

  case class DBRecord(
      id: UUID,
      name: String,
      link: String,
      price: Int,
      note: String
  )
  case class Record(
      id: UUID,
      name: String,
      link: String,
      price: Int,
      note: String,
      groups: List[UUID]
  )

  object Record {
    def apply(dbRecord: DBRecord, groups: List[UUID]): Record = new Record(
      id = dbRecord.id,
      name = dbRecord.name,
      link = dbRecord.link,
      price = dbRecord.price,
      note = dbRecord.note,
      groups = groups
    )
  }
}
