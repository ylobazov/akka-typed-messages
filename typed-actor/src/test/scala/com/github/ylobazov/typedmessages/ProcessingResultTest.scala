package com.github.ylobazov.processing

import com.github.ylobazov.typedmessages.logging.Context
import com.github.ylobazov.typedmessages.processing._
import org.scalatest.{FunSuite, Matchers}

import scala.concurrent.Future

class ProcessingResultTest extends FunSuite with Matchers {

  import scala.concurrent.ExecutionContext.Implicits.global

  private val ValidName = "valid"

  case class TestActionResponseEntity(name: String)

  case class TestAction(name: String)(implicit val ctx: Context) extends Action[TestActionResponseEntity]

  case class TestTrigger()(implicit val ctx: Context) extends Trigger

  //actor simulation
  def handle[R](action: Request[R]): Future[R] = {
    implicit val ctx: Context = action.ctx
    Future(
      action match {
        case TestAction(ValidName) => Completed(TestActionResponseEntity(ValidName))
        case TestAction(_) => Declined(NotFound, "Invalid filter")
        case TestTrigger() => /*do something*/
      }
    )
  }

  test("Action should return Completed when succeed") {
    implicit val ctx: Context = Context("reqId", "corrId")
    val action = TestAction(ValidName)
    handle(action).mapTo[action.Result].map { res =>
      assert(res.isSuccessful)
      res.foreach { resp =>
        resp shouldEqual TestActionResponseEntity(ValidName)
      }
    }
  }

  test("Action should return Declined when failed") {
    implicit val ctx: Context = Context("reqId", "corrId")
    val action = TestAction("invalid")
    handle(action).mapTo[action.Result].map(res => assert(res.isDeclined))
  }

  test("Trigger should return unit") {
    implicit val ctx: Context = Context("reqId", "corrId")
    val trigger = TestTrigger()
    handle(trigger).mapTo[trigger.Result].map { res => res shouldBe a[Unit] }
  }

}