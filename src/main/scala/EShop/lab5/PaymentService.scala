package EShop.lab5

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props, Status}
import akka.event.LoggingReceive
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.pattern.PipeToSupport
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}

object PaymentService {

  case object PaymentSucceeded // http status 200
  class PaymentClientError extends Exception // http statuses 400, 404
  class PaymentServerError extends Exception // http statuses 500, 408, 418

  def props(method: String, payment: ActorRef) = Props(new PaymentService(method, payment))

}

class PaymentService(method: String, payment: ActorRef) extends Actor with ActorLogging with PipeToSupport {

  import PaymentService._
  import context.dispatcher

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))
  private val http                                   = Http(context.system)
  private val URI                                    = getURI

  override def preStart(): Unit =
    http.singleRequest(HttpRequest(uri = URI)) pipeTo self //create http request (use http and uri)

  override def receive: Receive = LoggingReceive.withLabel("receive") {
    case HttpResponse(StatusCodes.OK, _, _, _) =>
      payment ! PaymentSucceeded
      self ! PoisonPill

    case HttpResponse(StatusCodes.InternalServerError | StatusCodes.RequestTimeout | StatusCodes.ImATeapot, _, _, _) =>
      throw new PaymentServerError

    case HttpResponse(StatusCodes.NotFound | StatusCodes.BadRequest, _, _, _) =>
      throw new PaymentClientError

    case Status.Failure(ex) => throw ex
  }

  private def getURI: String = method match {
    case "payu"   => "http://127.0.0.1:8080"
    case "paypal" => s"http://httpbin.org/status/408"
    case "visa"   => s"http://httpbin.org/status/200"
    case _        => s"http://httpbin.org/status/404"
  }

}
