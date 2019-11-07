package EShop.lab3

import EShop.lab2.Checkout.{PaymentStarted, ReceivePayment, SelectDeliveryMethod, SelectPayment, StartCheckout}
import EShop.lab2.{CartActor, Checkout}
import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.duration._


class CheckoutTest
  extends TestKit(ActorSystem("CheckoutTest"))
  with FlatSpecLike
  with ImplicitSender
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  implicit val timeout: Timeout = 1.second

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)

  it should "Send close confirmation to cart" in {

    val proxy = TestProbe()

    val fakeCartActor = system.actorOf(Props(new Actor {
      val checkoutActor = context.actorOf(Checkout.props(self))
      def receive = {
        case msg if sender == checkoutActor =>
          proxy.ref.forward(msg)

        case msg =>
          checkoutActor.forward(msg)

      }
    }))

    proxy.send(fakeCartActor, StartCheckout)
    proxy.send(fakeCartActor, SelectDeliveryMethod("paczkomat"))
    proxy.send(fakeCartActor, SelectPayment("paypal"))
    proxy.fishForMessage() {
      case _: PaymentStarted => true
    }
    proxy.send(fakeCartActor, ReceivePayment)

    proxy.expectMsg(CartActor.CloseCheckout)
  }
}
