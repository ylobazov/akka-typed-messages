package com.github.ylobazov.typedmessages

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.pipe
import akka.testkit.TestActor.KeepRunning
import akka.testkit.TestProbe
import com.github.ylobazov.typedmessages.logging.Context
import com.github.ylobazov.typedmessages.processing.Request

import scala.concurrent.{ExecutionContext, Future}

abstract class RuntimeTypedActorMock(implicit system: ActorSystem, ec: ExecutionContext) {

  def buildProbe: ActorRef = {
    val probe = TestProbe()
    probe.setAutoPilot((sender: ActorRef, msg: Any) => msg match {
      case r: Request[_] =>
        val res: Future[Any] = handle(r)(r.ctx)
        pipe(res)(ec).to(sender)
        KeepRunning
    })
    probe.ref
  }

  def handle[R](req: Request[R])(implicit ctx: Context): Future[req.Result]

}