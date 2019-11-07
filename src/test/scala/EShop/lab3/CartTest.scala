package EShop.lab3

import EShop.lab2.{Cart, CartActor}
import EShop.lab2.CartActor.{AddItem, CheckoutStarted, RemoveItem, StartCheckout}
import akka.actor.{ActorRef, ActorSystem, Cancellable, Props}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import akka.util.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.duration._

object CartTest {
  val emptyMsg      = "empty"
  val nonEmptyMsg   = "nonEmpty"
  val inCheckoutMsg = "inCheckout"

  def cartActorResponseOnStateChange(system: ActorSystem): ActorRef =
    system.actorOf(Props(new CartActor {
      override val cartTimerDuration: FiniteDuration = 1.seconds

      override def empty() = {
        sender ! emptyMsg
        super.empty
      }

      override def nonEmpty(cart: Cart, timer: Cancellable): Receive = {
        sender ! nonEmptyMsg
        super.nonEmpty(cart, timer)
      }

      override def inCheckout(cart: Cart): Receive = {
        sender ! inCheckoutMsg
        super.inCheckout(cart)
      }
    }))
}

class CartTest
  extends TestKit(ActorSystem("CartTest"))
  with FlatSpecLike
  with ImplicitSender
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)

  import CartTest._

  implicit val timeout: Timeout = 1.second

  //use GetItems command which was added to make test easier
  it should "add item properly" in {
    val testCartActor = TestActorRef(new CartActor())
    testCartActor ! AddItem("chleb")
    testCartActor ! AddItem("ser")
    testCartActor ! AddItem("jajka")
    testCartActor ! AddItem("pomidory")

    (testCartActor ? CartActor.GetItems).mapTo[Seq[Any]].futureValue shouldBe Seq[Any]("chleb", "ser", "jajka", "pomidory")
  }

  it should "be empty after adding and removing the same item" in {
    val testCartActor = TestActorRef(new CartActor())
    testCartActor ! AddItem("chleb")
    testCartActor ! AddItem("ser")
    testCartActor ! RemoveItem("chleb")
    testCartActor ! RemoveItem("ser")

    (testCartActor ? CartActor.GetItems).mapTo[Seq[Any]].futureValue shouldBe Seq.empty[Any]
  }

  it should "start checkout" in {
    val cart = cartActorResponseOnStateChange(system)
    cart ! AddItem("chleb")
    expectMsg(nonEmptyMsg)
    cart ! StartCheckout
    fishForMessage() {
      case _: CheckoutStarted => true
    }
    expectMsg(inCheckoutMsg)
  }
}

