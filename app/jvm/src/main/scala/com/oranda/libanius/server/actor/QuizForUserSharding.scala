package com.oranda.libanius.server.actor

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}

import com.oranda.libanius.actor.QuizForUserActor
import com.oranda.libanius.actor.QuizMessages._
import com.oranda.libanius.model.Quiz
import com.oranda.libanius.server.ConfExtra

object QuizForUserSharding {

  def startQuizForUserSharding(system: ActorSystem, quiz: Quiz): ActorRef = {
    ClusterSharding(system).start(
      typeName = "QuizForUser",
      entityProps = Props(new QuizForUserActor(quiz)),
      settings = ClusterShardingSettings(system),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId
    )
  }

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case qc: QuizMessage => (qc.userId.toString, qc)
  }

  // Assumes there are about 10 times more shards than the maximum planned amount of nodes
  val extractShardId: ShardRegion.ExtractShardId = {

    def computeShardId(entityId: ShardRegion.EntityId): ShardRegion.ShardId =
      (math.abs(entityId.hashCode()) % ConfExtra.maxNumShards).toString

    {
      case quizCommand: QuizMessage => computeShardId(quizCommand.userId.toString)
      case ShardRegion.StartEntity(id) => computeShardId(id)
    }
  }
}
