package io.asuna.totsuki

import com.websudos.phantom.CassandraTable
import com.websudos.phantom.dsl._
import io.asuna.asunasan.Config
import io.asuna.proto.ids.MatchId
import scala.concurrent.Future


class TotsukiDatabase(val keyspace: KeySpaceDef) extends Database[TotsukiDatabase](keyspace) {

  object matchSet extends MatchSetModel with keyspace.Connector

}

object TotsukiDatabase {

  def fromConfig(config: Config[TotsukiConfig]): TotsukiDatabase = {
    new TotsukiDatabase(
      ContactPoints(config.service.cassandraHosts)
        .keySpace(config.service.bacchusKeyspace))
  }

}

abstract class MatchSetModel extends CassandraTable[MatchSetModel, String] with RootConnector {

  override val tableName = "match_set"

  object id extends StringColumn(this) with PartitionKey[String]

  override def fromRow(row: Row): String = id(row)

  def add(id: MatchId): Future[ResultSet] = {
    insert
      .value(_.id, s"${id.region.name}/${id.id}").future()
  }

}
