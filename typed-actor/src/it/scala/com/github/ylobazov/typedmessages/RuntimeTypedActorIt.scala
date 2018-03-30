package com.github.ylobazov.typedmessages

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.routing.RoundRobinPool
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKit}
import com.github.ylobazov.typedmessages.logging.Context
import com.github.ylobazov.typedmessages.processing._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._

class RuntimeTypedActorIt extends TestKit(ActorSystem("RuntimeTypedActorItSystem"))
  with ImplicitSender
  with DefaultTimeout
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  import BaseActorIt._

  private val testActorPool: ActorRef = system.actorOf(RoundRobinPool(2).props(Props(classOf[TestActor], system)), "BaseActorItPool")

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "An actor, extended from BaseActor" should {
    "answer with Completed to successful Action" in {
      implicit val context: Context = Context("reqId", "corrId")
      val req = TestAction(ValidName)
      testActorPool ! req
      expectMsg(2.second, Completed(TestActionResponseEntity(ValidName)))
    }

    "answer with Declined to failed Action" in {
      implicit val context: Context = Context("reqId", "corrId")
      val req = TestAction("invalid")
      testActorPool ! req
      expectMsg(2.second, Declined(NotFound, "Invalid filter"))
    }

    "handle Trigger without answer" in {
      implicit val context: Context = Context("reqId", "corrId")
      val sideEffectStorage = mutable.Map[String, String]()
      testActorPool ! TestTrigger(sideEffectStorage)
      expectNoMessage(2.second)
      sideEffectStorage.get(StorageKey) shouldEqual Some(StorageValue)
    }
  }
}

object BaseActorIt {
  val ValidName = "valid"
  val StorageKey = "test"
  val StorageValue = "passed"
}

case class TestActionResponseEntity(name: String)
case class TestAction(name: String)(implicit val ctx: Context) extends Action[TestActionResponseEntity]
case class TestTrigger(sideEffectStorage: mutable.Map[String, String])(implicit val ctx: Context) extends Trigger


class TestActor(system: ActorSystem) extends RuntimeTypedActor {

  import BaseActorIt._

  override def handle[R](req: Request[R])(implicit ctx: Context): Future[req.Result] = {
    Future[req.Result](
      req match {
        case TestAction(ValidName) => Completed(TestActionResponseEntity(ValidName))
        case TestAction(_) => Declined(NotFound, "Invalid filter")
        case TestTrigger(sideEffectStorage) =>
          val _ = sideEffectStorage.put(StorageKey, StorageValue)
      }
    )(context.dispatcher)
  }
}

object TestActor {
  def props(system: ActorSystem): Props = Props(new TestActor(system))
}