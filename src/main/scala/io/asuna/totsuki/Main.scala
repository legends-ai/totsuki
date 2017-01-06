package io.asuna.totsuki

import monix.execution.Scheduler.Implicits.global

object Main {

  def main(args: Array[String]): Unit = {
    new TotsukiServer(args).standReady()
  }

}
