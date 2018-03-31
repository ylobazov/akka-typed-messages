package com.github.ylobazov.typedmessages

import java.time.LocalDateTime

import akka.actor.{ActorPath, ActorRef, ActorSelection, ActorSystem, Props}
import akka.pattern.ask
import akka.routing.RoundRobinPool
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKit}
import akka.util.Timeout
import com.github.ylobazov.typedmessages.logging._
import com.github.ylobazov.typedmessages.processing._
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

//MODEL
case class User(_id: String, name: String)

case class Bookmark(url: String)

//MESSAGES
case class GetUserBookmarksResponse(list: List[Bookmark])

case class GetUserBookmarks(usedId: String)(implicit val ctx: Context) extends Action[GetUserBookmarksResponse]

case class FindUserResponse(user: User)

case class FindUser(usedId: String)(implicit val ctx: Context) extends Action[FindUserResponse]

case class SyncWithCloud(userId: String, date: LocalDateTime, sideEffectStorage: mutable.Map[String, LocalDateTime])(implicit val ctx: Context) extends Trigger


//ACTORS
class UserActor extends RuntimeTypedActor {

  implicit val timeout: Timeout = 2.seconds

  override protected def handle[R](req: Request[R])(implicit ctx: Context): Future[req.Result] = {
    implicit val ec: ExecutionContext = context.dispatcher

    req match {
      case FindUser(usedId) =>
        val result = if ("milessabin" == usedId) {
          Future(Completed(FindUserResponse(User("milessabin", "Miles Sabin"))))
        } else {
          Future(Declined(NotFound, s"User with _id=[$usedId] does not exist"))
        }
        result

      case SyncWithCloud(userId, date, sideEffectStorage) => Future {
        val _ = sideEffectStorage.put(userId, date)
      }
    }
  }
}

object UserActor {
  def props(): Props = Props(new UserActor())
}


class BookmarksActor(userActorPath: ActorPath) extends RuntimeTypedActor {

  implicit val timeout: Timeout = 2.seconds

  import context.system

  override protected def handle[R](req: Request[R])(implicit ctx: Context): Future[req.Result] = {
    implicit val ec: ExecutionContext = context.dispatcher

    req match {
      case GetUserBookmarks(userId) =>
        val findUserReq = FindUser(userId)
        userActor.ask(findUserReq).mapTo[findUserReq.Result].mapR { resp =>
          if ("milessabin" == resp.user._id) {
            GetUserBookmarksResponse(List(Bookmark("http://127.0.0.1")))
          } else {
            GetUserBookmarksResponse(Nil)
          }
        }

    }
  }

  private def userActor: ActorSelection = system.actorSelection(userActorPath)
}

object BookmarksActor {
  def props(userActorPath: ActorPath): Props = Props(new BookmarksActor(userActorPath))
}

class ExampleTest extends TestKit(ActorSystem("BookmarksExampleActorSystem"))
  with ImplicitSender
  with DefaultTimeout
  with FlatSpecLike
  with Matchers
  with BeforeAndAfterAll {


  private val userActorPool: ActorRef = system.actorOf(RoundRobinPool(2).props(Props(classOf[UserActor])), "UserActorPool")
  private val bookmarkActorPool: ActorRef = system.actorOf(RoundRobinPool(2).props(Props(classOf[BookmarksActor], userActorPool.path)), "BookmarkActorPool")

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "UserActor" should "return Completed for existing user" in {
    implicit val context: Context = Context("reqId", "corrId")
    val req = FindUser("milessabin")
    userActorPool ! req
    expectMsg(2.second, Completed(FindUserResponse(User("milessabin", "Miles Sabin"))))
  }

  "UserActor" should "return Declined for unknown user" in {
    implicit val context: Context = Context("reqId", "corrId")
    val req = FindUser("ylobazov")
    userActorPool ! req
    expectMsg(2.second, Declined(NotFound, "User with _id=[ylobazov] does not exist"))
  }

  "UserActor" should "return Declined for unknown command" in {
    implicit val context: Context = Context("reqId", "corrId")
    val req = GetUserBookmarks("milessabin")
    userActorPool ! req
    expectMsgPF(2.second) {
      case declined@Declined(InternalError, _) => println(s"Error caught: $declined")
      case other => fail(s"Do not expect $other")
    }
  }

  "BookmarkActor" should "return list of bookmarks" in {
    implicit val context: Context = Context("reqId", "corrId")
    val req = GetUserBookmarks("milessabin")
    bookmarkActorPool ! req
    expectMsg(2.second, Completed(GetUserBookmarksResponse(List(Bookmark("http://127.0.0.1")))))
  }

  "BookmarkActor" should "return Declined for unknown user" in {
    implicit val context: Context = Context("reqId", "corrId")
    val req = GetUserBookmarks("ylobazov")
    bookmarkActorPool ! req
    expectMsg(2.second, Declined(NotFound, "User with _id=[ylobazov] does not exist"))
  }

  "UserActor" should "handle Trigger without an answer" in {
    val today = LocalDateTime.now
    implicit val context: Context = Context("reqId", "corrId")
    val sideEffectStorage = mutable.Map[String, LocalDateTime]()
    userActorPool ! SyncWithCloud("milessabin", today, sideEffectStorage)
    expectNoMessage(2.second)
    sideEffectStorage.get("milessabin") shouldEqual Some(today)
  }

}


