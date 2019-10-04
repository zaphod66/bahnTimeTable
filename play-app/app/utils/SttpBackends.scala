package utils

import cats.effect.IO
import com.softwaremill.sttp.{HttpURLConnectionBackend, MonadError, Request, Response, SttpBackend, _}
import com.typesafe.scalalogging.StrictLogging

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
  import scala.concurrent.duration._

  private val throttler = new Throttler(20, 60.seconds)

  override def send[T](request: Request[T, S]): R[Response[T]] = { throttler( delegate.send(request) ) }

  override def close(): Unit = delegate.close()

  override def responseMonad: MonadError[R] = delegate.responseMonad
}

class IOSttpBackend(delegate: SttpBackend[Id, Nothing]) extends SttpBackend[IO, Nothing] with StrictLogging {
  override def send[T](request: Request[T, Nothing]): IO[Response[T]] = IO { delegate.send(request) }

  override def close(): Unit = delegate.close()

  override def responseMonad: MonadError[IO] = IOMonad
}

class IOThrottlingSttpBackend(delegate: SttpBackend[IO, Nothing]) extends SttpBackend[IO, Nothing] with StrictLogging {
  import scala.concurrent.duration._

  private val throttler = new IOThrottler(20, 60.seconds)

  override def send[T](request: Request[T, Nothing]): IO[Response[T]] = throttler( delegate.send(request) ).flatMap(identity)

  override def close(): Unit = delegate.close()

  override def responseMonad: MonadError[IO] = delegate.responseMonad

  def avail: IO[Long] = throttler.avail
}

object IOMonad extends MonadError[IO] {
  override def unit[T](t: T): IO[T] = IO { t }
  override def map[T, T2](fa: IO[T])(f: (T) => T2): IO[T2] = fa.map(f)
  override def flatMap[T, T2](fa: IO[T])(f: (T) => IO[T2]): IO[T2] = fa.flatMap(f)

  override def error[T](t: Throwable): IO[T] = IO.raiseError(t)
  override protected def handleWrappedError[T](rt: IO[T])(h: PartialFunction[Throwable, IO[T]]): IO[T] =
    rt.handleErrorWith(h)
}
