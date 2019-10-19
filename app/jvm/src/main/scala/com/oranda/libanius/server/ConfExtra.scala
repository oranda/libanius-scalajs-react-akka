package com.oranda.libanius.server

import com.typesafe.config.ConfigFactory

object ConfExtra {
  lazy val config = ConfigFactory.load()
  val appVersion = config.getString("libanius.appVersion")
  val maxNumShards = config.getInt("libanius.akka.cluster.maxNumShards")
}
