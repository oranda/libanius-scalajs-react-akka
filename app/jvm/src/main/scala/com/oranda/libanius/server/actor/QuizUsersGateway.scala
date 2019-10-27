package com.oranda.libanius.server.actor

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import com.oranda.libanius.actor.QuizMessages._
import com.oranda.libanius.actor.QuizEvents._
import com.oranda.libanius.actor.UserId
import com.oranda.libanius.model.Quiz
import com.oranda.libanius.model.quizitem.QuizItemViewWithChoices
import com.oranda.libanius.scalajs.{QuizGroupKeyReact, QuizItemAnswer, QuizItemReact}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class QuizUsersGateway(val system: ActorSystem) {

  implicit val executionContext = system.dispatcher

  // The default timeout for ?.
  implicit val askTimeout = Timeout(30.seconds)

  val quiz = Quiz.getDefaultQuiz
  val quizForUserShardRegion = QuizForUserSharding.startQuizForUserSharding(system, quiz)

  def produceQuizItem(userId: UserId): Future[Option[QuizItemViewWithChoices]] =
    (quizForUserShardRegion ? ProduceQuizItem(userId)).mapTo[Option[QuizItemViewWithChoices]]

  def scoreSoFar(userId: UserId): Future[BigDecimal] =
    (quizForUserShardRegion ? ScoreSoFar(userId)).mapTo[BigDecimal]

  def updateWithUserResponse(userId: UserId, qia: QuizItemAnswer): Future[Boolean] = {
    (quizForUserShardRegion ? UpdateWithUserResponse(
      userId,
      QuizGroupKeyReact.toQgKey(qia.quizGroupKey),
      qia.prompt,
      qia.correctResponse,
      qia.isCorrect
    )).mapTo[Boolean]
  }

  def activateQuizGroup(userId: UserId, qgKey: QuizGroupKeyReact): Future[QuizGroupActivated] =
    (quizForUserShardRegion ? ActivateQuizGroup(
      userId,
      QuizGroupKeyReact.toQgKey(qgKey),
      singleGroupActiveMode = true
    )).mapTo[QuizGroupActivated]

  def removeQuizItem(userId: UserId, quizItemReact: QuizItemReact) = {
    (quizForUserShardRegion ? RemoveQuizItem(
      userId,
      QuizGroupKeyReact.toQgKey(quizItemReact.quizGroupKey),
      quizItemReact.prompt,
      quizItemReact.correctResponse
    )).mapTo[QuizItemRemoved]
  }
}
