package utils

import cats.effect.{Fiber, IO}
import cats.effect.concurrent.Semaphore
import com.typesafe.scalalogging.StrictLogging
import utils.Throttler.{lastAccess, logger, token}

import scala.concurrent.ExecutionContext

class Throttler(token: Int, durationMillis: Long) extends StrictLogging {

  private implicit val ctx = IO.contextShift(ExecutionContext.global)
  private implicit val timer = IO.timer(ExecutionContext.global)

  private var localToken = token

  private val sem = Semaphore[IO](token)

  private def releaseToken(): IO[Unit] = {
    import scala.concurrent.blocking

    blocking { Thread.sleep(durationMillis) }

    sem.map(_.release)

    val threadId = Thread.currentThread().getId
    val ll = sem.flatMap(_.available).unsafeRunSync()

    logger.info(s"threadId: $threadId - token: $token - avail: $ll")

    IO(())
  }

  def throttle[T](body: => T): T = {

    val threadId = Thread.currentThread().getId

    val ll = sem.flatMap(_.available).unsafeRunSync()

    logger.info(s"threadId: $threadId - token: $token - avail: $ll")

    val f = for {
      _ <- sem.map(_.acquire)
      _ <- releaseToken().start
    } yield body

    val res = f.unsafeRunSync()

    res
  }
}

object Throttler extends StrictLogging {
  private val delta      = 3000L  // in millis
  private var lastAccess = System.currentTimeMillis() - delta
  private var token      = 3

  def bracket[T](body: => T): T = {
    val threadId = Thread.currentThread().getId

    this.synchronized {
      val newAccess  = System.currentTimeMillis()
      val waitLast   = newAccess - lastAccess
      val tmp        = delta - waitLast
      val actualWait = if (tmp < 0) 0 else tmp

      token = token + (waitLast / delta).toInt
      if (token > 3) token = 3

      logger.info(s"threadId: $threadId - lastAccess: $lastAccess - newAccess: $newAccess - waitLast: $waitLast - actualWait: $actualWait - token: $token")

      val t = body

      if (token <= 0) {
        Thread.sleep(actualWait)
      } else {
        token = token - 1
      }

      lastAccess = System.currentTimeMillis()

      t
    }
  }
}
