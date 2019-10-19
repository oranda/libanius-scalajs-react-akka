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

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.stream.ActorMaterializer

import com.oranda.libanius.actor.UserId
import com.oranda.libanius.dependencies.AppDependencyAccess
import com.oranda.libanius.scalajs._
import com.oranda.libanius.server.actor.QuizUsersGateway
import com.softwaremill.session.{InMemoryRefreshTokenStorage, SessionConfig, SessionManager, SessionSerializer, SingleValueSessionSerializer}
import com.softwaremill.session.CsrfDirectives._
import com.softwaremill.session.CsrfOptions._
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Properties, Success, Try}
import upickle.{default => upickle}

object Server extends HttpApp with AppDependencyAccess {

  implicit val system = ActorSystem("libanius")
  implicit val materializer = ActorMaterializer()

  implicit val sessionManager = new SessionManager[UserId](SessionConfig.fromConfig())

  implicit def serializer: SessionSerializer[UserId, String] =
    new SingleValueSessionSerializer(
      _.id.toString,
      (id: String) => Try { UserId(UUID.fromString(id)) }
    )

  // Allow "remember-me" sessions
  implicit val refreshTokenStorage = new InMemoryRefreshTokenStorage[UserId] {
    def log(msg: String) = l.log(msg)
  }

  def startSession(v: UserId) = setSession(refreshable, usingCookies, v)

  private val onlyInSession = requiredSession(refreshable[UserId], usingCookies)
  private val optSession = optionalSession(refreshable[UserId], usingCookies)

  private val quizUsersGateway = new QuizUsersGateway(system)
  private val quizUsersService = new QuizUsersService(quizUsersGateway)

  override def routes: Route = {

    get {
      pathSingleSlash {
        complete {
          HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            QuizScreen.skeleton().render
          )
        }
      } ~
      path("findNextQuizItem") {
        onlyInSession { userId =>
          val newQuizItemToClient = quizUsersService.findNextQuizItem(userId)
          onComplete(newQuizItemToClient) {
            case Success(f) => complete(newQuizItemToClient.map(upickle.write(_)))
            case Failure(ex) => complete(StatusCodes.InternalServerError)
          }
        }
      } ~
      // serve other requests directly from the resource directory
      getFromResourceDirectory("")
    } ~
    post {
      path("staticQuizData") {
        // first call of the session: set a cookie with a userId if one does not already exist
        optSession { session =>
          session match {
            case Some(_) =>
              val staticDataToClient: StaticDataToClient = QuizUsersService.staticQuizData
              complete(upickle.write(staticDataToClient))
            case None =>
              val userId = UserId(UUID.randomUUID())
              startSession(userId) {
                setNewCsrfToken(checkHeader) { ctx =>
                  val staticDataToClient: StaticDataToClient = QuizUsersService.staticQuizData
                  ctx.complete(upickle.write(staticDataToClient))
                }
              }
          }
        }
      } ~
      path("processUserResponse") {
        onlyInSession { userId =>
          entity(as[String]) { e =>
            val quizItemAnswer = upickle.read[QuizItemAnswer](e)
            val userResponse = quizUsersService.processUserResponse(
              userId,
              quizItemAnswer
            )
            onComplete(userResponse) {
              case Success(f) => complete(userResponse.map(upickle.write(_)))
              case Failure(ex) => complete(StatusCodes.InternalServerError)
            }
          }
        }
      } ~
      path("removeQuizItem") {
        onlyInSession { userId =>
          entity(as[String]) { e =>
            val quizItemReact = upickle.read[QuizItemReact](e)
            onComplete(quizUsersService.removeQuizItem(userId, quizItemReact)) {
              case Success(f) => complete("OK")
              case Failure(ex) => complete(StatusCodes.InternalServerError)
            }
          }
        }
      } ~
      path("loadNewQuiz") {
        onlyInSession { userId =>
          entity(as[String]) { e =>
            val lnqRequest = upickle.read[LoadNewQuizRequest](e)
            onComplete(quizUsersService.loadNewQuiz(userId, lnqRequest)) {
              case Success(f) => complete("OK")
              case Failure(ex) => complete(StatusCodes.InternalServerError)
            }
          }
        }
      }
    }
  }

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load().getConfig("libanius")

    // If on Heroku, the application must get the port from the environment.
    val port = Properties.envOrElse("PORT", config.getString("port")).toInt

    Http().bindAndHandle(handler = routes, interface = "0.0.0.0", port = port) map { binding =>
      l.log(s"REST interface bound to 0.0.0.0:$port") } recover { case ex =>
      l.log(s"REST interface could not bind to 0.0.0.0:$port", ex.getMessage)
    }
  }
}
