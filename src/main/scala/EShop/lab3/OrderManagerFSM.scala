package EShop.lab3

import EShop.lab2.{CartActor, CartFSM, Checkout}
import EShop.lab3.OrderManager._
import akka.actor.{ActorRef, FSM}

class OrderManagerFSM extends FSM[State, Data] {

  import OrderManager._

  startWith(Uninitialized, Empty)

  when(Uninitialized) {
    case Event(AddItem(i), Empty) =>
      val cartActor = context.system.actorOf(CartFSM.props())
      cartActor ! CartActor.AddItem(i)
      sender() ! Done
      goto(Open).using(CartData(cartActor))
  }

  when(Open) {
    case Event(AddItem(i), CartData(cartActor)) =>
      cartActor ! CartActor.AddItem(i)
      sender() ! Done
      stay.using(CartData(cartActor))

    case Event(RemoveItem(i), CartData(cartActor)) =>
      cartActor ! CartActor.RemoveItem(i)
      sender() ! Done
      stay.using(CartData(cartActor))

    case Event(Buy, CartData(cartActor)) =>
      cartActor ! CartActor.StartCheckout
      goto(InCheckout).using(CartDataWithSender(cartActor, sender()))
  }

  when(InCheckout) {
    case Event(CartActor.CheckoutStarted(checkoutRef, _), CartDataWithSender(_, senderRef)) =>
      senderRef ! Done
      stay.using(InCheckoutData(checkoutRef))

    case Event(SelectDeliveryAndPaymentMethod(delivery, payment), InCheckoutData(checkoutRef)) =>
      checkoutRef ! Checkout.SelectDeliveryMethod(delivery)
      checkoutRef ! Checkout.SelectPayment(payment)
      goto(InPayment).using(InPaymentData(sender()))
  }

  when(InPayment) {
    case Event(Checkout.PaymentStarted(paymentRef), InPaymentData(senderRef)) =>
      senderRef ! Done
      stay.using(InPaymentDataWithSender(paymentRef, senderRef))

    case Event(Pay, InPaymentDataWithSender(paymentRef, _)) =>
      paymentRef ! Payment.DoPayment
      stay.using(InPaymentDataWithSender(paymentRef, sender()))

    case Event(Payment.PaymentConfirmed, InPaymentDataWithSender(_, senderRef)) =>
      senderRef ! Done
      goto(Finished).using(Empty)
  }

  when(Finished) {
    case _ =>
      sender ! "order manager finished job"
      stay()
  }
}
