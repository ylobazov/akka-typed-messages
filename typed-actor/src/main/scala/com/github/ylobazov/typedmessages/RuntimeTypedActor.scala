package com.github.ylobazov.typedmessages

import akka.actor.Actor
import akka.pattern.pipe
import com.github.ylobazov.typedmessages.logging._
import com.github.ylobazov.typedmessages.processing._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Try}

abstract class RuntimeTypedActor extends Actor with ContextLoggingSupport {

  protected def handle[R](req: Request[R])(implicit ctx: Context): Future[req.Result]

  override def receive: Receive = {
    case t: Trigger =>
      implicit val ctx = t.ctx
      Try(handle(t)(ctx)) match {
        case Failure(e: MatchError) =>
          log.error(s"Match error at ${self.path}: $e")
        case Failure(NonFatal(e)) =>
          log.error(s"Unexpected error occurred during ${t.getClass.getSimpleName} message processing by ${self.path}: $e")
        case _ => ()
      }
    case a: Action[_] =>
      implicit val ec: ExecutionContext = context.dispatcher
      implicit val ctx: Context = a.ctx

      Try(handle(a)).recover {
        case e: MatchError =>
          log.error(s"[$ctx] Match error at ${self.path}: $e")
          Future(
            Declined(InternalError,
              s"${self.path} is unable to handle ${a.getClass.getSimpleName} message due to match error"
            )
          )
        case NonFatal(e) =>
          log.error(s"[$ctx] Unexpected error occurred during ${a.getClass.getSimpleName} message processing by ${self.path}: $e")
          Future(
            Declined(InternalError,
              s"Unexpected error occurred during ${a.getClass.getSimpleName} message processing by ${self.path}: ${e.getMessage}"
            )
          )
      }.foreach(_.pipeTo(sender))
  }
}