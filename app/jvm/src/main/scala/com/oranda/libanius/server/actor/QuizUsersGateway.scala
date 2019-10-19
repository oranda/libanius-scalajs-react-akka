package com.oranda.libanius.server.actor

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import com.oranda.libanius.actor.QuizForUserActor._
import com.oranda.libanius.actor.UserId
import com.oranda.libanius.model.quizitem.QuizItemViewWithChoices
import com.oranda.libanius.scalajs.{QuizGroupKey, QuizItemAnswer, QuizItemReact}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class QuizUsersGateway(val system: ActorSystem) {

  implicit val executionContext = system.dispatcher

  // The default timeout for ?.
  implicit val askTimeout = Timeout(30.seconds)

  val quizForUserShardRegion = QuizForUserSharding.startQuizForUserSharding(system)

  def produceQuizItem(userId: UserId): Future[Option[QuizItemViewWithChoices]] =
    (quizForUserShardRegion ? ProduceQuizItem(userId)).mapTo[Option[QuizItemViewWithChoices]]

  def scoreSoFar(userId: UserId): Future[BigDecimal] =
    (quizForUserShardRegion ? ScoreSoFar(userId)).mapTo[BigDecimal]

  def updateWithUserResponse(userId: UserId, qia: QuizItemAnswer): Future[Boolean] = {
    (quizForUserShardRegion ? UpdateWithUserResponse(
      userId,
      qia.prompt,
      qia.correctResponse,
      qia.promptType,
      qia.responseType,
      qia.isCorrect
    )).mapTo[Boolean]
  }

  def activateQuizGroup(userId: UserId, qgKey: QuizGroupKey): Future[QuizGroupActivated] =
    (quizForUserShardRegion ? ActivateQuizGroup(
      userId,
      qgKey.promptType,
      qgKey.responseType,
      singleGroupActiveMode = true
    )).mapTo[QuizGroupActivated]

  def removeQuizItem(userId: UserId, quizItemReact: QuizItemReact) = {
    (quizForUserShardRegion ? RemoveQuizItem(
      userId,
      quizItemReact.promptType,
      quizItemReact.responseType,
      quizItemReact.prompt,
      quizItemReact.correctResponse
    )).mapTo[QuizItemRemoved]
  }
}
