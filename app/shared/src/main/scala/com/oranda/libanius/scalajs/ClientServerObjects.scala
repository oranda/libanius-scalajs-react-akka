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

package com.oranda.libanius.scalajs

import com.oranda.libanius.model.quizgroup.{QuizGroupKey, QuizGroupType}
import com.oranda.libanius.model.quizitem.{QuizItemResponse, QuizItemViewWithChoices}
import upickle.default.{macroRW, ReadWriter => RW}

abstract class DataToClient

// Data that changes so infrequently it only needs to be sent on loading a new quiz
case class StaticDataToClient(appVersion: String, quizGroupHeaders: Seq[QuizGroupKeyReact])
  extends DataToClient

object StaticDataToClient {
  implicit def rw: RW[StaticDataToClient] = macroRW
}

case class NewQuizItemToClient(quizItemReact: Option[QuizItemReact], scoreText: String)
  extends DataToClient

object NewQuizItemToClient {
  implicit def rw: RW[NewQuizItemToClient] = macroRW
}

case class QuizGroupKeyReact(promptType: String, responseType: String, quizGroupType: String)

object QuizGroupKeyReact {
  implicit def rw: RW[QuizGroupKeyReact] = macroRW

  def apply(qgKey: QuizGroupKey): QuizGroupKeyReact =
    QuizGroupKeyReact(qgKey.promptType, qgKey.responseType, qgKey.quizGroupType.str)

  def toQgKey(qgKeyReact: QuizGroupKeyReact) =
    QuizGroupKey(
      qgKeyReact.promptType,
      qgKeyReact.responseType,
      QuizGroupType.fromString(qgKeyReact.quizGroupType)
    )
}

// Note: for promptResponseMap, ListMap does not work with upickle
case class QuizItemReact(
  prompt: String,
  correctResponse: String,
  quizGroupKey: QuizGroupKeyReact,
  numCorrectResponsesInARow: Int,
  promptResponseMap: Seq[(String, String)]
) {
  def allChoices: Iterable[String] = promptResponseMap.map { case (prompt, response) => prompt }

  lazy val promptType = quizGroupKey.promptType
  lazy val responseType = quizGroupKey.responseType
}

object QuizItemReact {
  implicit def rw: RW[QuizItemReact] = macroRW

  // Should be apply, but upickle complains.
  def construct(qi: QuizItemViewWithChoices): QuizItemReact =
    QuizItemReact(
      qi.prompt,
      qi.correctResponse,
      QuizGroupKeyReact(qi.quizGroupKey),
      qi.numCorrectResponsesInARow,
      qi.promptResponseMap)
}

abstract class RequestToServer

case class LoadNewQuizRequest(qgKey: QuizGroupKeyReact) extends RequestToServer

object LoadNewQuizRequest {
  implicit def rw: RW[LoadNewQuizRequest] = macroRW
}

case class QuizItemResponseReact(
  quizGroupKey: QuizGroupKeyReact,
  prompt: String,
  response: String,
  correctResponse: String
) {
  lazy val isCorrect = response == correctResponse
}

object QuizItemResponseReact {
  implicit def rw: RW[QuizItemResponseReact] = macroRW
  // Should be apply, but upickle complains.
  def construct(qi: QuizItemReact, choice: String): QuizItemResponseReact =
    QuizItemResponseReact(qi.quizGroupKey, qi.prompt, choice, qi.correctResponse)
}
