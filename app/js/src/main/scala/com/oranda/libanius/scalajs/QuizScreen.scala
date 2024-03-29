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

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom.document
import org.scalajs.dom.ext.Ajax

import scala.scalajs.js.timers._
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scalajs.concurrent.JSExecutionContext.Implicits.runNow
import upickle.{default => upickle}

@JSExportTopLevel("QuizScreen")
object QuizScreen {

  case class State(
      appVersion: String,
      availableQuizGroups: Seq[QuizGroupKeyReact] = Seq.empty,
      currentQuizItem: Option[QuizItemReact] = None,
      prevQuizItem: Option[QuizItemReact] = None,
      scoreText: String = "",
      chosen: Option[String] = None,
      status: String = "") {

    def quizNotStarted = !currentQuizItem.isDefined && !prevQuizItem.isDefined
    def quizEnded = !currentQuizItem.isDefined && prevQuizItem.isDefined

    def onNewQuizItem(newQuizItem: Option[QuizItemReact], score: String): State =
      State(appVersion, availableQuizGroups, newQuizItem, currentQuizItem, score)

    def otherQuizGroups =
      currentQuizItem.map { cqi =>
        availableQuizGroups.filterNot(
          qg => qg.promptType == cqi.promptType && qg.promptType == cqi.responseType)
      }.getOrElse(availableQuizGroups)
  }

  private[this] def newQuizStateFromStaticData(responseText: String): State = {
    val quizData = upickle.read[StaticDataToClient](responseText)
    val appVersion = quizData.appVersion
    val availableQuizGroups: Seq[QuizGroupKeyReact] = quizData.quizGroupHeaders
    State(appVersion, availableQuizGroups, None, None, "0.0%")
  }

  private[this] def newQuizStateFromQuizItem(state: State, responseText: String): State = {
    val newQuizItem = upickle.read[NewQuizItemToClient](responseText)
    state.onNewQuizItem(newQuizItem.quizItemReact, newQuizItem.scoreText)
  }

  class Backend($: BackendScope[String, State]) {
    def start() =
      $.state.map { s =>
        Ajax.post("/staticQuizData").foreach { xhr =>
          val state = newQuizStateFromStaticData(xhr.responseText)
          Ajax.get("/findNextQuizItem").foreach { xhr =>
            $.setState(newQuizStateFromQuizItem(state, xhr.responseText)).runNow()
          }
        }
      }

    def render(state: State): VdomElement =
      state.currentQuizItem match {
        // Only show the page if there is a quiz item
        case Some(currentQuizItem: QuizItemReact) =>
          <.div(
            <.span(^.id := "header-wrapper", ScoreText(state.scoreText),
              <.span(^.className := "alignright",
                <.button(^.id := "delete-button",
                  ^.onClick --> removeCurrentWordAndShowNextItem(state, currentQuizItem),
                  "DELETE WORD"))
            ),
            QuestionArea(Question(currentQuizItem.prompt,
              currentQuizItem.responseType,
              currentQuizItem.numCorrectResponsesInARow)),
            <.span(currentQuizItem.allChoices.toTagMod { choice =>
              <.div(
                <.p(<.button(
                  ^.className := "response-choice-button",
                  ^.className := cssClassForChosen(choice, state.chosen,
                    currentQuizItem.correctResponse),
                  ^.onClick --> submitResponse(state, choice, currentQuizItem), choice))
              )
            }),
            PreviousQuizItemArea(state.prevQuizItem),
            StatusText(state.status),
            <.br(), <.br(), <.br(), <.br(),
            <.span(
              <.span(^.id := "other-quiz-groups-header", "Other Quiz Groups"),
              <.br(), <.br(), <.br(),
              state.otherQuizGroups.toTagMod(qgKey =>
                <.span(
                  ^.onClick --> loadNewQuiz(state, qgKey),
                  ^.className := "other-quiz-group-text",
                  <.a(s"${qgKey.promptType} - ${qgKey.responseType}"),
                  <.br(), <.br()))
            ),
            <.br(), <.br(), <.br(), <.br(),
            DescriptiveText(state.appVersion)
          )
        case None =>
          if (!state.quizEnded)
            <.div("Loading...")
          else
            <.div(s"Congratulations! Quiz complete. Score: ${state.scoreText}")
      }

    def submitResponse(state: State, choice: String, curQuizItem: QuizItemReact) = Callback {
      $.modState(_.copy(chosen = Option(choice))).runNow()
      val url = "/processUserResponse"
      val response = QuizItemResponseReact.construct(curQuizItem, choice)
      val data = upickle.write(response)

      val sleepMillis: Double = if (response.isCorrect) 200 else 1000
      Ajax.post(url, data).foreach { xhr =>
        setTimeout(sleepMillis) {
          $.setState(newQuizStateFromQuizItem(state, xhr.responseText)).runNow()
        }
      }
    }

    private def removeCurrentWordAndShowNextItem(state: State, curQuizItem: QuizItemReact) = Callback {
      val url = "/removeQuizItem"
      val data = upickle.write(curQuizItem)
      Ajax.post(url, data).foreach { _ =>
        Ajax.get(s"/findNextQuizItem").foreach { xhr =>
          $.setState(newQuizStateFromQuizItem(state, xhr.responseText)).runNow()
        }
      }
    }

    private def loadNewQuiz(state: State, qgKey: QuizGroupKeyReact) = Callback {
      val url = "/loadNewQuiz"
      val data = upickle.write(LoadNewQuizRequest(qgKey))
      Ajax.post(url, data).foreach { _ =>
        Ajax.get(s"/findNextQuizItem").foreach { xhr =>
          $.setState(newQuizStateFromQuizItem(state, xhr.responseText)).runNow()
        }
      }
    }
  }

  val ScoreText = ScalaComponent.builder[String]("ScoreText")
    .render_P(scoreText => <.span(^.id := "score-text", ^.className := "alignleft",
        s"Score: $scoreText"))
    .build

  case class Question(promptWord: String, responseType: String, numCorrectResponsesInARow: Int)

  val QuestionArea = ScalaComponent.builder[Question]("Question")
    .render_P(question =>
      <.span(
        <.span(^.id :=  "prompt-word", question.promptWord),
        <.p(^.id :=  "question-text",
          "What is the ", <.span(^.id := "response-type", question.responseType), "? ",
          <.span("(correctly answered ", question.numCorrectResponsesInARow, " times)")),
        <.br()))
    .build

  val PreviousPrompt = ScalaComponent.builder[String]("PreviousPrompt")
    .render_P(prevPrompt => <.span(^.id := "prev-prompt", ^.className := "alignleft",
        "PREV: ",
         <.span(prevPrompt)
      )
    )
    .build

  val PreviousChoices = ScalaComponent.builder[Seq[(String, String)]]("PreviousChoices")
    .render_P(prevChoices =>
      <.span(^.className := "alignright",
        prevChoices.toTagMod { case (prevPrompt, prevResponses) =>
          TagMod(<.span(
            <.div(^.className := "alignleft prev-choice",
              s"$prevPrompt = $prevResponses"
            ), <.br()
          ))
        })).build

  val PreviousQuizItemArea = ScalaComponent.builder[Option[QuizItemReact]]("PreviousQuizItem")
    .render_P(_ match {
        case Some(previousQuizItem: QuizItemReact) =>
          <.span(^.id := "footer-wrapper",
            PreviousPrompt(previousQuizItem.prompt),
            PreviousChoices(previousQuizItem.promptResponseMap))
        case None => <.span()
      }).build

  val StatusText = ScalaComponent.builder[String]("StatusText")
    .render_P(statusText => <.p(^.className := "status-text", statusText))
    .build

  val DescriptiveText = ScalaComponent.builder[String]("DescriptiveText")
    .render_P(appVersion => <.span(^.id := "descriptive-text",
        <.a(^.href := "https://github.com/oranda/libanius-scalajs-react-akka",
          "libanius-scalajs-react-akka"),
        <.span(s" v$appVersion by "),
        <.a(^.href := "https://scala-bility.blogspot.de/", "James McCabe")))
    .build

  private[this] def cssClassForChosen(
      buttonValue: String,
      chosen: Option[String],
      correctResponse: String): String =
    chosen match {
      case None => ""
      case Some(chosenResponse) =>
        if (correctResponse == buttonValue) "correct-response"
        else {
          if (chosenResponse != buttonValue) "" else "incorrect-response"
        }
    }

  val QuizScreen = ScalaComponent.builder[String]("QuizScreen")
    .initialStateFromProps(appVersion => State(appVersion))
    .backend(new Backend(_))
    .renderBackend
    .componentDidMount(_.backend.start)
    .build

  @JSExport
  def main(appVersion: String): Unit =
    QuizScreen(appVersion).renderIntoDOM(document.getElementById("container"))
}

