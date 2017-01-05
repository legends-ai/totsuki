package io.asuna.totsuki

import com.google.protobuf.empty.Empty
import io.asuna.asunasan.BaseService
import io.asuna.proto.bacchus.BacchusData.RawMatch
import io.asuna.proto.service_totsuki.TotsukiGrpc
import scala.concurrent.{ Future }
import scala.concurrent.{ ExecutionContext }

class TotsukiServer(args: Seq[String])(implicit ec: ExecutionContext)
    extends BaseService(args, TotsukiConfigParser, TotsukiGrpc.bindService) with TotsukiGrpc.Totsuki {

  override def write(rawMatch: RawMatch): Future[Empty] = endpoint {
    Future.successful(Empty.defaultInstance)
  }

}
