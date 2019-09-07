package utils

import com.softwaremill.sttp.{MonadError, Response, SttpBackend}
import com.typesafe.scalalogging.StrictLogging
import com.softwaremill.sttp.{Request, _}
import scala.language.higherKinds

class LoggingSttpBackend[R[_], S](delegate: SttpBackend[R, S]) extends SttpBackend[R, S] with StrictLogging {

  override def send[T](request: Request[T, S]): R[Response[T]] = {
    responseMonad.map(responseMonad.handleError(delegate.send(request)) {
      case e: Exception =>
        logger.error(s"Exception when sending request: $request.\nTo reproduce ,run: ${request.toCurl}", e)

        responseMonad.error(e)
    }) { response =>
      logger.trace(s"=====\n$request => $response")
      logger.info(request.toCurl)

      response
    }
  }

  override def close(): Unit = delegate.close()

  override def responseMonad: MonadError[R] = delegate.responseMonad
}

class ThrottlingSttpBackend[R[_], S](delegate: SttpBackend[R, S]) extends SttpBackend[R, S] with StrictLogging {

//  private val throttler = new Throttler(20, 60000)
  private val throttler = new Throttler(2, 6000)

  override def send[T](request: Request[T, S]): R[Response[T]] = {

//    Throttler.bracket( delegate.send(request) )
    throttler.throttle( delegate.send(request) )

  }

  override def close(): Unit = delegate.close()

  override def responseMonad: MonadError[R] = delegate.responseMonad
}
