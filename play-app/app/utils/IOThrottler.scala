package utils

import cats.effect.{ContextShift, IO, Timer}
import cats.effect.concurrent.Semaphore
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

class IOThrottler(token: Int, duration: FiniteDuration) extends StrictLogging {

  private implicit val ctx: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  private implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

  private val sem = Semaphore[IO](token).unsafeRunSync()

  private def semAcquire: IO[Unit] = {
    val threadId = Thread.currentThread().getId

    for {
      _ <- sem.acquire
      a <- sem.available
      _ = logger.info(s"acquire -> threadId: $threadId - tokens left: $a")
    } yield ()
  }

  private def semRelease: IO[Unit] = {
    val threadId = Thread.currentThread().getId

    for {
      _ <- sem.release
      a <- sem.available
      _ = logger.info(s"release -> threadId: $threadId - tokens left: $a")
    } yield ()
  }

  private def throttle[T](thunk: => T): IO[T] = {
    import cats.syntax.apply._

    for {
      _ <- semAcquire
      res = thunk
      _ <- (timer.sleep(duration) *> semRelease).start
    } yield res
  }

  def apply[T](thunk: => T): IO[T] = throttle(thunk)
}
