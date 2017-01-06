package io.asuna.totsuki

import monix.execution.Scheduler.Implicits.global

object Main {

  def main(args: Array[String]): Unit = {
    val totsuki = new TotsukiServer(args)
    totsuki.standReady()
  }

}
