package utils

import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration.FiniteDuration

class Throttler(token: Int, duration: FiniteDuration) extends StrictLogging {
  import java.util.concurrent.Semaphore

  private val sem =  new Semaphore(token)

  private def throttle[T](thunk: => T): T = {
    val threadId = Thread.currentThread().getId

    sem.acquire()
    DelayedFuture(duration)(sem.release())

    logger.info(s"threadId: $threadId - tokens left: ${sem.availablePermits()}")

    thunk
  }

  def apply[T](thunk: => T): T = throttle(thunk)
}
