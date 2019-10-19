Libanius-ScalaJs-React-Akka
===========================

Libanius is an app to aid learning. Basically it presents "quiz items" to the user, and for each one the user must select the correct answer option. Quiz items are presented at random according to a certain algorithm. An item has to be answered correctly several times before it is considered learnt.

The core use is as a vocabulary builder in a new language, but it is designed to be flexible enough to present questions and answers of all types.

The implementation of Libanius is in Scala. There are Android and Web-based interfaces.

This project is the latest attempt at a Web interface to Libanius. It is implemented using Scala.js and React. The core Libanius code is located here: https://github.com/oranda/libanius-akka

Suggestions for new features and code improvements will be happily received by:

James McCabe <jjtmccabe@gmail.com>


Install
=======

You need to have Scala installed to run Libanius-ScalaJs-React-Akka. It has been tested with Scala 2.12.6, Java 8, and sbt 1.2.8.

To install, either download the zip file for this project or clone it with git:

    git clone git://github.com/oranda/libanius-scalajs-react-akka

Then cd to the libanius-scalajs-react directory and run it:

    sbt appJVM/run

Then just open your browser at http://localhost:8080/

Different users will get their own separate instances of the quiz.


Implementation
==============

This front-end to Libanius uses Scala.js to convert Scala code to JavaScript and HTML on the front-end (app/js folder).

The scala-js-react library is used to model front-end components and event handling according to Facebook's React framework.

Ajax calls are made to the QuizUsersService which runs on an akka http server (app/jvm folder). The service 
communicates with actors to fetch quiz items. One actor is assigned for each user. The actors are maintained using
Akka's Cluster Sharding.

Data is maintained on the server using the Akka Persistence implementation of event sourcing.


Screenshots
===========

![Libanius](https://github.com/oranda/libanius-scalajs-react/raw/master/docs/libanius-scalajs-react-v0.2-screenshot.png)


Demo
====

As of writing, Libanius is deployed on Heroku at:

https://thawing-stream-3905.herokuapp.com/

However, this may not be supported indefinitely.


License
=======

Most Libanius-ScalaJs-React-Akka source files are made available under the terms of the GNU Affero General Public License (AGPL).
See individual files for details.

Attribution info is in [SOURCES](SOURCES.md).
