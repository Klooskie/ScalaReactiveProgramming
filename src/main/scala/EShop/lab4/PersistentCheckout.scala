package EShop.lab4

import EShop.lab2.CartActor
import EShop.lab3.Payment
import akka.actor.{ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}
import akka.persistence.PersistentActor

import scala.util.Random
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object PersistentCheckout {

  def props(cartActor: ActorRef, persistenceId: String) =
    Props(new PersistentCheckout(cartActor, persistenceId))
}

class PersistentCheckout(
  cartActor: ActorRef,
  val persistenceId: String
) extends PersistentActor {

  import EShop.lab2.Checkout._

  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)
  val timerDuration     = 1.seconds

  private def updateState(event: Event, maybeTimer: Option[Cancellable] = None): Unit = {
    context.become(
      event match {
        case CheckoutStarted =>
          selectingDelivery(scheduler.scheduleOnce(timerDuration, self, ExpireCheckout))

        case DeliveryMethodSelected(method) =>
          selectingPaymentMethod(maybeTimer.getOrElse(scheduler.scheduleOnce(timerDuration, self, ExpireCheckout)))

        case CheckOutClosed =>
          if (maybeTimer.isDefined)
            maybeTimer.get.cancel()
          closed

        case CheckoutCancelled =>
          if (maybeTimer.isDefined)
            maybeTimer.get.cancel()
          cancelled

        case PaymentStarted(payment) =>
          if (maybeTimer.isDefined)
            maybeTimer.get.cancel()
          processingPayment(scheduler.scheduleOnce(timerDuration, self, ExpirePayment))
      }
    )
  }

  def receiveCommand: Receive = LoggingReceive {
    case StartCheckout =>
      persist(CheckoutStarted) { event =>
        updateState(event)
      }
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case SelectDeliveryMethod(method) =>
      persist(DeliveryMethodSelected(method)) { event =>
        updateState(event, Some(timer))
      }

    case CancelCheckout =>
      persist(CheckoutCancelled) { event =>
        updateState(event, Some(timer))
      }

    case ExpireCheckout =>
      persist(CheckoutCancelled) { event =>
        updateState(event)
      }
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive {
    case SelectPayment(payment) =>
      val paymentActor = context.system.actorOf(Payment.props(payment, sender(), self))
      persist(PaymentStarted(paymentActor)) { event =>
        sender() ! PaymentStarted(paymentActor)
        updateState(event, Some(timer))
      }

    case CancelCheckout =>
      persist(CheckoutCancelled) { event =>
        updateState(event, Some(timer))
      }

    case ExpireCheckout =>
      persist(CheckoutCancelled) { event =>
        updateState(event)
      }
  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive {
    case ReceivePayment =>
      persist(CheckOutClosed) { event =>
        cartActor ! CartActor.CloseCheckout
        updateState(event, Some(timer))
      }
      cartActor ! CartActor.CloseCheckout

    case CancelCheckout =>
      persist(CheckoutCancelled) { event =>
        updateState(event, Some(timer))
      }

    case ExpirePayment =>
      persist(CheckoutCancelled) { event =>
        updateState(event)
      }
  }

  def cancelled: Receive = LoggingReceive {
    case StartCheckout =>
      persist(CheckoutStarted) { event =>
        updateState(event)
      }
  }

  def closed: Receive = LoggingReceive {
    case StartCheckout =>
      persist(CheckoutStarted) { event =>
        updateState(event)
      }
  }

  override def receiveRecover: Receive = LoggingReceive {
    case event: Event => updateState(event)
  }
}
