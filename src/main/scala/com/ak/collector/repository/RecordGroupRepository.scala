package com.ak.collector.repository

import java.util.UUID
import doobie.implicits._
import doobie.postgres.implicits._

object RecordGroupRepository {
    def create(recordId: UUID, groupId: UUID) = sql"""
        INSERT INTO record_group (record_id, group_id) VALUES (${recordId}, ${groupId})
    """.update.run

    def delete(recordId: UUID, groupId: UUID) = sql"""
        DELETE from record_group WHERE record_id=${recordId} AND group_id=${groupId}
    """.update.run

    def selectGroupsByRecordId(recordId: UUID) = sql""" 
        SELECT group_id FROM record_group WHERE record_id=${recordId}
    """.query[UUID].to[List]
}