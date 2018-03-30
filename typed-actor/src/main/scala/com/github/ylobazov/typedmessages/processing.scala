package com.github.ylobazov.typedmessages

import logging.Context

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

object processing {

  trait Request[R] {
    type Result = R
    val ctx: Context
  }

  //Request, which assumes some result
  abstract class Action[R: ClassTag] extends Request[ProcessingResult[R]]

  // Request, which does not assume any result
  trait Trigger extends Request[Unit]

  sealed trait ProcessingResult[+T] {
    val ctx: Context
    val status: ProcessingStatus

    def map[B](f: T => B): ProcessingResult[B]

    def foreach[U](f: T => U): Unit

    def filter(p: T => Boolean): ProcessingResult[T]

    def isSuccessful: Boolean

    def flatten[B](implicit ev: T <:< ProcessingResult[B]): ProcessingResult[B]

    def flatMap[B](f: T => ProcessingResult[B]): ProcessingResult[B] = this.map(f).flatten

    def isDeclined: Boolean = !isSuccessful

    def andThen[B](f: T => ProcessingResult[B]): ProcessingResult[B] = flatMap(f)

    def andThen[B](f: => ProcessingResult[B]): ProcessingResult[B] = flatMap(_ => f)

    def getOrElse[B >: T](alternative: B): B

    def fold[B](default: => B)(f: T => B): B
  }

  case class Completed[+R](result: R, status: ProcessingStatus = OkStatus)(implicit val ctx: Context) extends ProcessingResult[R] {

    override def flatten[B](implicit ev: R <:< ProcessingResult[B]): ProcessingResult[B] = ev(result)

    override def foreach[U](f: (R) => U): Unit = {
      val _ = f(result)
    }

    override def map[B](f: (R) => B): ProcessingResult[B] = Completed(f(result))

    override def filter(p: (R) => Boolean): ProcessingResult[R] =
      if (p(result)) this else Declined(IllegalArgument, s"Value [$result] didn't satisfy a predicate")

    override def isSuccessful: Boolean = true

    override def getOrElse[B >: R](alternative: B): B = result

    override def fold[B](default: => B)(f: (R) => B): B = f(result)
  }

  class Ok(status: ProcessingStatus)(implicit ctx: Context) extends Completed({}, status)

  object Ok {
    def apply(status: ProcessingStatus = OkStatus)(implicit ctx: Context): Ok = new Ok(status)
  }

  case class Declined(status: ProcessingStatus, message: String)(implicit val ctx: Context) extends ProcessingResult[Nothing] {
    override def map[B](f: (Nothing) => B): ProcessingResult[B] = this

    override def flatten[B](implicit ev: Nothing <:< ProcessingResult[B]): ProcessingResult[B] = this

    override def filter(p: (Nothing) => Boolean): ProcessingResult[Nothing] = this

    override def foreach[U](f: (Nothing) => U): Unit = {
      val _ = ()
    }

    override def isSuccessful: Boolean = false

    override def getOrElse[B >: Nothing](alternative: B): B = alternative

    override def fold[B](default: => B)(f: (Nothing) => B): B = default
  }

  case class FutureProcessingResultTransformer[A](v: Future[ProcessingResult[A]]) {
    def map[B](f: A => B)(implicit ec: ExecutionContext): FutureProcessingResultTransformer[B] =
      FutureProcessingResultTransformer(v.map(_.map(f)))

    def flatMap[B](f: A => FutureProcessingResultTransformer[B])(implicit ec: ExecutionContext): FutureProcessingResultTransformer[B] = {
      FutureProcessingResultTransformer(v.flatMap {
        case completed: Completed[A] => f(completed.result).v
        case declined: Declined => Future.successful(declined)
      })
    }

  }

  implicit class ImplicitFutureProcessingResult[A](v: Future[ProcessingResult[A]]) {
    def toTransformer = FutureProcessingResultTransformer(v)

    def mapR[B](f: A => B)(implicit ec: ExecutionContext): Future[ProcessingResult[B]] =
      FutureProcessingResultTransformer(v).map(f).v

    def flatMapR[B](f: A => Future[ProcessingResult[B]])(implicit ec: ExecutionContext): Future[ProcessingResult[B]] =
      FutureProcessingResultTransformer(v).flatMap(x => FutureProcessingResultTransformer(f(x))).v

    def unwind(implicit ec: ExecutionContext): Future[(Context, A)] = v.flatMap {
      case x@Completed(result, _) => Future.successful((x.ctx, result))
      case x@Declined(status, message) => Future.failed(ProcessDeclinedException(x.ctx, status, message))
    }
  }

  sealed trait ProcessingStatus

  case object OkStatus extends ProcessingStatus
  case object IllegalArgument extends ProcessingStatus
  case object NotFound extends ProcessingStatus
  case object InternalError extends ProcessingStatus
  case object ExternalSystemNotAvailable extends ProcessingStatus
  case object ExternalSystemError extends ProcessingStatus
  case object AuthenticationFailed extends ProcessingStatus
  case object TooManyRequests extends ProcessingStatus
  case object PartialContent extends ProcessingStatus
  case class ProcessDeclinedException(ctx: Context, status: ProcessingStatus, message: String) extends Exception(message)

}
