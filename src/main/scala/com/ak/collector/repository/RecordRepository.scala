package com.ak.collector.repository

import java.util.UUID

import cats.effect.Sync
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import fs2.Stream
import cats.implicits._
import RecordRepository._
import CustomInput._
import UserRepository.User

trait RecordStore[F[_]] {
  def create(record: RecordRequest): F[UUID]
  def update(id: UUID, record: RecordRequest): F[Option[UUID]]
  def delete(id: UUID): F[Option[Unit]]
  def getById(id: UUID): F[Option[Record]]
  def findByLink(link: String): F[Option[Record]]
  def recordsHeadersByGroup(groupId: UUID): F[List[String]]
  def recordsByGroup(groupId: UUID): Stream[F, DBRecord]
}

class RecordRepository[F[_]: Sync](transactor: Transactor[F]) {
  def store(user: User) = new RecordStore[F] {
    def create(record: RecordRequest): F[UUID] = (for {
      recordId <- sql"""
        INSERT INTO records (name, link, note, custom_inputs, author) VALUES (${record.name}, ${record.link}, ${record.note}, ${record.customInputs}, ${user.id})
      """.update.withUniqueGeneratedKeys[UUID]("id")
      _ <- record.groups.map(RecordGroupRepository.create(recordId, _)).sequence_
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
        UPDATE records SET name = ${record.name}, link = ${record.link}, note = ${record.note}, custom_inputs = ${record.customInputs}
        WHERE id = $id AND (author = ${user.id} OR author IS NULL)
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
        DELETE FROM records WHERE id = $id AND (author = ${user.id} OR author IS NULL)
      """.update.run
      .transact(transactor)
      .map(affectedRows => {
        if (affectedRows == 1) {
          Some(())
        } else {
          None
        }
      })

    private def getByIdOp(id: UUID) =
      (
        sql"""
        SELECT id, name, link, note, custom_inputs FROM records WHERE id = $id AND (author = ${user.id} OR author IS NULL)
      """.query[DBRecord].option,
        RecordGroupRepository.selectGroupsByRecordId(id)
      ).mapN((record, groups) => record.map(Record(_, groups)))

    def getById(id: UUID): F[Option[Record]] = getByIdOp(id).transact(transactor)

    def findByLink(link: String): F[Option[Record]] = (for {
      resultOpt <- sql"""
        SELECT id, name, link, note, custom_inputs FROM records WHERE link = $link AND (author = ${user.id} OR author IS NULL)
      """.query[DBRecord].option
      groups <- resultOpt match {
        case Some(result) => RecordGroupRepository.selectGroupsByRecordId(result.id)
        case None         => List.empty[UUID].pure[ConnectionIO]
      }
    } yield resultOpt.map(Record(_, groups)))
      .transact(transactor)

    def recordsHeadersByGroup(groupId: UUID): F[List[String]] = sql"""
      SELECT custom_inputs FROM records JOIN record_group ON records.id = record_group.record_id 
        WHERE record_group.group_id = ${groupId} AND (author = ${user.id} OR author IS NULL)"""
      .query[List[CustomInput]]
      .stream
      .compile
      .fold(Set.empty[String])(_ ++ _.map(_.name).toSet)
      .transact[F](transactor)
      .map(fields => List("id", "name", "link", "note") ++ fields.toList)

    def recordsByGroup(groupId: UUID): Stream[F, DBRecord] = sql"""
      SELECT id, name, link, note, custom_inputs FROM records JOIN record_group ON records.id = record_group.record_id
        WHERE record_group.group_id = ${groupId} AND (author = ${user.id} OR author IS NULL)
    """.query[DBRecord].stream.transact[F](transactor)
  }
}

object RecordRepository {

  case class RecordRequest(
      name: String,
      link: String,
      note: String,
      groups: List[UUID],
      customInputs: List[CustomInput]
  )

  case class DBRecord(
      id: UUID,
      name: String,
      link: String,
      note: String,
      customInputs: List[CustomInput]
  )
  case class Record(
      id: UUID,
      name: String,
      link: String,
      note: String,
      customInputs: List[CustomInput],
      groups: List[UUID]
  )

  object Record {
    def apply(dbRecord: DBRecord, groups: List[UUID]): Record = new Record(
      id = dbRecord.id,
      name = dbRecord.name,
      link = dbRecord.link,
      note = dbRecord.note,
      customInputs = dbRecord.customInputs,
      groups = groups
    )
  }
}
