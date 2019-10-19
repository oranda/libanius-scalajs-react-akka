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

import com.oranda.libanius.model.quizgroup.QuizGroupHeader
import com.oranda.libanius.model.quizitem.QuizItemViewWithChoices
import upickle.default.{macroRW, ReadWriter => RW}

abstract class DataToClient

// Data that changes so infrequently it only needs to be sent on loading a new quiz
case class StaticDataToClient(appVersion: String, quizGroupHeaders: Seq[QuizGroupKey])
  extends DataToClient

object StaticDataToClient {
  implicit def rw: RW[StaticDataToClient] = macroRW
}

case class NewQuizItemToClient(quizItemReact: Option[QuizItemReact], scoreText: String)
  extends DataToClient

object NewQuizItemToClient {
  implicit def rw: RW[NewQuizItemToClient] = macroRW
}

case class QuizGroupKey(promptType: String, responseType: String, quizGroupType: String)

object QuizGroupKey {
  implicit def rw: RW[QuizGroupKey] = macroRW

  def fromQgh(qgh: QuizGroupHeader) =
    QuizGroupKey(qgh.promptType, qgh.responseType, qgh.quizGroupType.str)
}

// Note: for promptResponseMap, ListMap does not work with upickle
case class QuizItemReact(
  prompt: String,
  correctResponse: String,
  promptType: String,
  responseType: String,
  numCorrectResponsesInARow: Int,
  promptResponseMap: Seq[(String, String)]
) {
  def allChoices: Iterable[String] = promptResponseMap.map { case (prompt, response) => prompt }
}

object QuizItemReact {
  implicit def rw: RW[QuizItemReact] = macroRW

  // Should be apply, but upickle complains.
  def construct(qi: QuizItemViewWithChoices): QuizItemReact =
    QuizItemReact(
      qi.prompt,
      qi.correctResponse,
      qi.promptType,
      qi.responseType,
      qi.numCorrectResponsesInARow,
      qi.promptResponseMap)
}

abstract class RequestToServer

case class LoadNewQuizRequest(qgKey: QuizGroupKey) extends RequestToServer

object LoadNewQuizRequest {
  implicit def rw: RW[LoadNewQuizRequest] = macroRW
}

case class QuizItemAnswer(
  prompt: String,
  correctResponse: String,
  promptType: String,
  responseType: String,
  choice: String
) {
  val isCorrect = correctResponse == choice
}

object QuizItemAnswer {

  implicit def rw: RW[QuizItemAnswer] = macroRW
  // Should be apply, but upickle complains.
  def construct(qi: QuizItemReact, choice: String): QuizItemAnswer =
    QuizItemAnswer(qi.prompt, qi.correctResponse, qi.promptType, qi.responseType, choice)
}
