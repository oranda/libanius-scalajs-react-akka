package com.oranda.libanius.server.actor

import java.util.UUID

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import com.oranda.libanius.actor.QuizForUserActor._
import com.oranda.libanius.actor.{QuizForUserActor, UserId}
import com.oranda.libanius.dependencies.AppDependencyAccess
import com.oranda.libanius.model.{Correct, Incorrect, ItemNotFound, Quiz}
import com.oranda.libanius.model.quizgroup.{QuizGroupKey, QuizGroupType}
import com.oranda.libanius.model.quizitem.{QuizItem, QuizItemViewWithChoices}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class QuizForUserShardingSpec extends TestKit(ActorSystem("libanius-test"))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with AppDependencyAccess {

  private val quizGroupKey = QuizGroupKey("English word", "German word", QuizGroupType.WordMapping)
  private val userId = UserId(UUID.randomUUID())

  private val quiz = Quiz.demoQuiz()
  private def newQuizShardActor = QuizForUserSharding.startQuizForUserSharding(system, quiz)

  // avoid all those dead letter error messages at the end
  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A QuizForUserSharding actor" must {
    "return the score so far" in {
      newQuizShardActor ! ScoreSoFar(userId)
      val expectedScore = BigDecimal(0)
      expectMsg(expectedScore)
    }

    "produce a quiz item" in {
      newQuizShardActor ! ProduceQuizItem(userId)
      val quizItemView: Option[QuizItemViewWithChoices] =
        expectMsgType[Option[QuizItemViewWithChoices]]
      val quizItem = quizItemView.map(_.quizItem)
      val quizItemExpected = Some(QuizItem("en route", "unterwegs"))
      assert(quizItem == quizItemExpected)
    }

    "update the quiz on a user response" in {
      val quizActor = newQuizShardActor
      quizActor ! UpdateWithUserResponse(userId, quizGroupKey, "en route", "unterwegs", true)
      expectMsgType[QuizUpdatedWithUserResponse]

      // get the score and make sure it is not zero, showing that the quiz was updated
      quizActor ! ScoreSoFar(userId)
      val score: BigDecimal = expectMsgType[BigDecimal]
      assert(score > 0)
    }

    "confirm a response is correct" in {
      newQuizShardActor ! IsResponseCorrect(userId, quizGroupKey, "en route", "unterwegs")
      expectMsg(Correct)
    }

    "confirm a response is incorrect" in {
      newQuizShardActor ! IsResponseCorrect(userId, quizGroupKey, "en route", "unterschrift")
      expectMsg(Incorrect)
    }

    "return NotFound for an isCorrect call on a nonexistent item" in {
      newQuizShardActor ! IsResponseCorrect(userId, quizGroupKey, "non-existent", "unterschrift")
      expectMsg(ItemNotFound)
    }

    "remove a quiz item" in {
      val quizActor = newQuizShardActor
      quizActor ! RemoveQuizItem(userId, quizGroupKey, "en route", "unterwegs")
      expectMsg(QuizItemRemoved(quizGroupKey, "en route", "unterwegs"))
      quizActor ! IsResponseCorrect(userId, quizGroupKey, "en route", "unterwegs")
      expectMsg(ItemNotFound)
    }

    "activate a quiz group" in {
      val demoQuiz = Quiz.demoQuiz()
      demoQuiz.findQuizGroupHeader(quizGroupKey) match {
        case Some(quizGroupHeader) =>
          // set up the actor with a quiz whose only group is inactive
          val demoQuizInactive = demoQuiz.deactivate(quizGroupHeader)
          val quizActor = system.actorOf(Props(new QuizForUserActor(demoQuizInactive)))

          val testCorrectMessage = IsResponseCorrect(userId, quizGroupKey, "en route", "unterwegs")

          quizActor ! testCorrectMessage
          expectMsg(ItemNotFound) // fails because the group is inactive

          quizActor ! ActivateQuizGroup(userId, quizGroupKey, true)
          expectMsg(QuizGroupActivated(quizGroupKey, true))

          quizActor ! testCorrectMessage
          expectMsg(Correct) // succeeds because the group is active
        case None => throw new RuntimeException(s"$quizGroupKey not found in demo quiz")
      }
    }
  }
}


  /*
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
   */
