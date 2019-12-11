package EShop.lab2

import EShop.lab3.Payment
import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                       extends Command
  case class SelectDeliveryMethod(method: String) extends Command
  case object CancelCheckout                      extends Command
  case object ExpireCheckout                      extends Command
  case class SelectPayment(payment: String)       extends Command
  case object ExpirePayment                       extends Command
  case object ReceivePayment                      extends Command
  case object Expire                              extends Command

  sealed trait Event
  case object CheckOutClosed                        extends Event
  case class PaymentStarted(payment: ActorRef)      extends Event
  case object CheckoutStarted                       extends Event
  case object CheckoutCancelled                     extends Event
  case class DeliveryMethodSelected(method: String) extends Event

  def props(cart: ActorRef) = Props(new Checkout(cart))
}

class Checkout(
  cartActor: ActorRef
) extends Actor {

  import Checkout._

  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)

  val checkoutTimerDuration = 1 seconds
  val paymentTimerDuration  = 1 seconds

  def receive: Receive = LoggingReceive {
    case StartCheckout =>
      log.debug("Starting checkout (becoming selectingDelivery)")
      context become selectingDelivery(scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout))
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case SelectDeliveryMethod(method) =>
      log.debug("Delivery method selected - " + method + "(becoming selectingPaymentMethod)")
      context become selectingPaymentMethod(timer)

    case CancelCheckout =>
      timer.cancel()
      log.debug("Canceling checkout (becoming cancelled)")
      context become cancelled

    case ExpireCheckout =>
      log.debug("Expiring checkout (becoming cancelled)")
      context become cancelled
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive {
    case SelectPayment(payment) =>
      timer.cancel()
      log.debug("Payment selected - " + payment + "(becoming processingPayment)")
      val paymentActor = context.system.actorOf(Payment.props(payment, sender(), self))
      sender() ! PaymentStarted(paymentActor)
      context become processingPayment(scheduler.scheduleOnce(paymentTimerDuration, self, ExpirePayment))

    case CancelCheckout =>
      timer.cancel()
      log.debug("Canceling checkout (becoming cancelled)")
      context become cancelled

    case ExpireCheckout =>
      log.debug("Expiring checkout (becoming cancelled)")
      context become cancelled
  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive {
    case ReceivePayment =>
      timer.cancel()
      log.debug("Payment received (becoming closed)")
      cartActor ! CartActor.CloseCheckout
      context become closed

    case CancelCheckout =>
      timer.cancel()
      log.debug("Canceling checkout (becoming cancelled)")
      context become cancelled

    case ExpirePayment =>
      log.debug("Expiring payment (becoming cancelled)")
      context become cancelled
  }

  def cancelled: Receive = LoggingReceive {
    case StartCheckout =>
      log.debug("Starting checkout (becoming selectingDelivery)")
      context become selectingDelivery(scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout))
  }

  def closed: Receive = LoggingReceive {
    case StartCheckout =>
      log.debug("Starting checkout (becoming selectingDelivery)")
      context become selectingDelivery(scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout))
  }
}
