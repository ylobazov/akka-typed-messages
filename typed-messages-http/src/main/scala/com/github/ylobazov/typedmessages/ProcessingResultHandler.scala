package com.github.ylobazov.typedmessages

import akka.http.scaladsl.model._
import com.github.ylobazov.typedmessages.processing._
import io.circe.generic.extras.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Printer}

trait ProcessingResultHandler {
  self: CirceConfigurationSupport =>

  def handleResult[E](result: ProcessingResult[E], customHandler: PartialFunction[ProcessingStatus, StatusCode] = Map.empty)
                     (implicit encoder: Encoder[E]): HttpResponse = {
    result match {
      case Completed((), status) =>
        HttpResponse(status = statusCode(status, customHandler))
      case Completed(entity, status) =>
        HttpResponse(status = statusCode(status, customHandler), entity = jsonResponse(entity))
      case Declined(status, message) =>
        HttpResponse(
          status = statusCode(status, customHandler),
          entity = jsonResponse(ErrorEntity(status.toString, message))
        )
    }
  }

  private def defaultStatusCodeFor(status: ProcessingStatus) = {
    status match {
      case OkStatus => StatusCodes.OK
      case IllegalArgument => StatusCodes.BadRequest
      case NotFound => StatusCodes.NotFound
      case InternalError => StatusCodes.InternalServerError
      case ExternalSystemError => StatusCodes.BadGateway
      case ExternalSystemNotAvailable => StatusCodes.BadGateway
      case AuthenticationFailed => StatusCodes.ProxyAuthenticationRequired
      case TooManyRequests => StatusCodes.TooManyRequests
      case PartialContent => StatusCodes.PartialContent
    }
  }

  private def statusCode(status: ProcessingStatus, customHandler: PartialFunction[ProcessingStatus, StatusCode]) =
    customHandler.lift(status).getOrElse(defaultStatusCodeFor(status))

  private def jsonResponse[E](responseEntity: E)(implicit encoder: Encoder[E], printer: Printer): ResponseEntity =
    HttpEntity(ContentType(MediaTypes.`application/json`), responseEntity.asJson.pretty(printer))

  case class ErrorEntity(status: String, message: String)

  object ErrorEntity {
    implicit val errorEntityEncoder: Encoder[ErrorEntity] = deriveEncoder
    implicit val errorEntityDecoder: Decoder[ErrorEntity] = deriveDecoder
  }

}

