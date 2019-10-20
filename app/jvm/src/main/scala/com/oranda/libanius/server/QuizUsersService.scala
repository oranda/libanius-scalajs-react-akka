/*
 * Libanius
 * Copyright (C) 2012-2019 James McCabe <jjtmccabe@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.oranda.libanius.server

import com.oranda.libanius.actor.QuizForUserActor.{QuizGroupActivated, QuizItemRemoved}
import com.oranda.libanius.actor.UserId
import com.oranda.libanius.dependencies.AppDependencyAccess
import com.oranda.libanius.model.Quiz
import com.oranda.libanius.model.quizgroup.QuizGroupHeader
import com.oranda.libanius.model.quizitem._
import com.oranda.libanius.scalajs._
import com.oranda.libanius.server.actor.QuizUsersGateway
import com.oranda.libanius.util.StringUtil
import com.typesafe.config.ConfigFactory

import scala.collection.immutable.Set
import scala.concurrent._

class QuizUsersService(quizUsersGateway: QuizUsersGateway) extends AppDependencyAccess {

  implicit val executionContext = quizUsersGateway.system.dispatcher

  def loadNewQuiz(userId: UserId, lnqRequest: LoadNewQuizRequest): Future[QuizGroupActivated] =
    quizUsersGateway.activateQuizGroup(userId, lnqRequest.qgKey)

  def removeQuizItem(userId: UserId, quizItemReact: QuizItemReact): Future[QuizItemRemoved] =
    quizUsersGateway.removeQuizItem(userId, quizItemReact)

  def processUserResponse(userId: UserId, qia: QuizItemAnswer): Future[NewQuizItemToClient] = {
    quizUsersGateway.updateWithUserResponse(userId, qia)
    findNextQuizItem(userId)
  }

  def findNextQuizItem(userId: UserId): Future[NewQuizItemToClient] = {
    def toNewQuizItemToClient(quizItem: Option[QuizItemReact], scoreText: String) =
      NewQuizItemToClient(quizItem, scoreText)

    findQuizItem(userId).zipWith(scoreText(userId))(toNewQuizItemToClient)
  }

  private[this] def findQuizItem(userId: UserId): Future[Option[QuizItemReact]] = {
    def toQuizItemReact(qi: Option[QuizItemViewWithChoices]): Option[QuizItemReact] =
      qi.map(QuizItemReact.construct(_))

    quizUsersGateway.produceQuizItem(userId).map(toQuizItemReact(_))
  }

  private[this] def scoreText(userId: UserId): Future[String] =
    quizUsersGateway.scoreSoFar(userId).map(StringUtil.formatScore(_))
}

object QuizUsersService extends AppDependencyAccess {

  private[this] val config = ConfigFactory.load().getConfig("libanius")

  def staticQuizData: StaticDataToClient = {
    // Refresh the availableQuizGroups in case another source has altered them.
    val availableQuizGroups: Set[QuizGroupHeader] = dataStore.findAvailableQuizGroups
    def makeQuizGroupKey(qgh: QuizGroupHeader) =
      QuizGroupKeyReact(qgh.promptType, qgh.responseType, qgh.quizGroupType.str)
    val quizGroupHeaders = availableQuizGroups.map(qgh => makeQuizGroupKey(qgh)).toSeq
    val appVersion = config.getString("appVersion")
    StaticDataToClient(appVersion, quizGroupHeaders)
  }
}
