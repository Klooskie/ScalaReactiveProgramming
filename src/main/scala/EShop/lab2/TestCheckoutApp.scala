package EShop.lab2

import EShop.lab2.Checkout.{CancelCheckout, ReceivePayment, SelectDeliveryMethod, SelectPayment, StartCheckout}
import akka.actor.{ActorSystem, Props}

object TestCheckoutApp extends App {
  val system = ActorSystem("Reactive2")

  val checkoutActor = system.actorOf(Props[Checkout], "checkoutActor")

  checkoutActor ! StartCheckout
  checkoutActor ! SelectDeliveryMethod("kurier")
  checkoutActor ! CancelCheckout
  checkoutActor ! StartCheckout
  checkoutActor ! SelectDeliveryMethod("kurier")
  checkoutActor ! SelectPayment("za pobraniem")
  checkoutActor ! ReceivePayment
  checkoutActor ! CancelCheckout
  checkoutActor ! CancelCheckout

  val checkoutFSMActor = system.actorOf(Props[CheckoutFSM], "checkoutFSMActor")

  checkoutFSMActor ! StartCheckout
  checkoutFSMActor ! SelectDeliveryMethod("kurier")
  checkoutFSMActor ! CancelCheckout
  checkoutFSMActor ! StartCheckout
  checkoutFSMActor ! SelectDeliveryMethod("kurier")
  checkoutFSMActor ! SelectPayment("za pobraniem")
  checkoutFSMActor ! ReceivePayment
  checkoutFSMActor ! CancelCheckout
  checkoutFSMActor ! CancelCheckout
}
