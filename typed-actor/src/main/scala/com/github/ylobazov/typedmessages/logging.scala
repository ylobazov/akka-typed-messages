package com.github.ylobazov.typedmessages

import com.typesafe.scalalogging.{CanLog, Logger}

object logging {

  case class Context(requestId: String, correlationId: String)

  implicit case object CanLogContext extends CanLog[Context] {
    override def logMessage(originalMsg: String, ctx: Context): String =
      s"requestId=[${ctx.requestId}], correlationId=[${ctx.correlationId}] $originalMsg"
  }


  trait ContextLoggingSupport {
    val log = Logger.takingImplicit[Context](this.getClass)
  }

}
