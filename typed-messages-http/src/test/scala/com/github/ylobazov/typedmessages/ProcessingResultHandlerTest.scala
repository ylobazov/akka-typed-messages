package com.github.ylobazov.typedmessages

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, MediaTypes, StatusCodes}
import akka.stream.scaladsl.{Flow, Sink}
import akka.testkit.TestKit
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.ByteString
import com.github.ylobazov.typedmessages.logging.Context
import com.github.ylobazov.typedmessages.processing._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser._
import io.circe.{Decoder, Encoder, Printer}
import org.scalatest.{FlatSpec, FlatSpecLike, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._


class ProcessingResultHandlerTest extends FlatSpec with ScalatestRouteTest with Matchers with ProcessingResultHandler with CirceConfigurationSupport {

  case class Dummy(intField: Int, stringField: String)

  implicit val ctx = Context("reqId", "corrId")

  implicit val circeConfiguration = Configuration.default.withSnakeCaseKeys
  implicit val printer  = Printer(
    preserveOrder = true,
    dropNullKeys = true,
    indent = "  ",
    lbraceRight = "\n",
    rbraceLeft = "\n",
    lbracketRight = "\n",
    rbracketLeft = "\n",
    lrbracketsEmpty = "\n",
    arrayCommaRight = "\n",
    objectCommaRight = "\n",
    colonLeft = " ",
    colonRight = " "
  )

  implicit val dummyEntityEncoder: Encoder[Dummy] = deriveEncoder
  implicit val dummyEntityDecoder: Decoder[Dummy] = deriveDecoder


  it should "support custom status handler" in {
    val testEntity = Dummy(42, "Some string")
    val result = Completed(testEntity)(Context("reqId", "corrId"))
    val httpResponse = handleResult(result, {
      case OkStatus => StatusCodes.Accepted
    })

    httpResponse shouldBe a[HttpResponse]
    httpResponse.status shouldEqual StatusCodes.Accepted
    httpResponse.entity.contentType.value shouldEqual MediaTypes.`application/json`.value

    val actualBody = decode[Dummy](extractBodyAsString(httpResponse.entity))
    assert(actualBody.isRight)
    actualBody foreach { entity =>
      entity shouldEqual testEntity
    }
  }

  it should "handle Completed action result" in {
    val testEntity = Dummy(42, "Some string")
    val result = Completed(testEntity)
    val httpResponse = handleResult(result)
    httpResponse shouldBe a[HttpResponse]
    httpResponse.status shouldEqual StatusCodes.OK
    httpResponse.entity.contentType.value shouldEqual MediaTypes.`application/json`.value

    val actualBody = decode[Dummy](extractBodyAsString(httpResponse.entity))
    assert(actualBody.isRight)
    actualBody foreach { entity =>
      entity shouldEqual testEntity
    }
  }

  it should "handle Ok action result" in {
    val result = Ok()
    val httpResponse = handleResult(result)
    httpResponse shouldBe a[HttpResponse]
    httpResponse.status shouldEqual StatusCodes.OK
  }

  it should "handle Declined action result" in {
    val result = Declined(InternalError, "Dunno what happened")
    val httpResponse = handleResult(result)
    httpResponse shouldBe a[HttpResponse]
    httpResponse.status shouldEqual StatusCodes.InternalServerError
    httpResponse.entity.contentType.value shouldEqual MediaTypes.`application/json`.value

    val actualBody = decode[ErrorEntity](extractBodyAsString(httpResponse.entity))
    assert(actualBody.isRight)
    actualBody foreach { entity =>
      entity.status shouldEqual result.status.toString
      entity.message shouldEqual result.message
    }
  }

  private def extractBodyAsString(httpEntity: HttpEntity): String =
    Await.result(
      httpEntity.dataBytes.via(
        Flow[ByteString].map(_.utf8String)
      ).runWith(Sink.head[String]),
      1.second
    )
}


