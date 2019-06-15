package utils

import com.typesafe.scalalogging.StrictLogging

object Throttler extends StrictLogging {
  private val delta      = 3000L  // in millis
  private var lastAccess = System.currentTimeMillis() - delta
  private var lastInc    = 0L
  private var token      = 20

  def bracket[T](body: => T): T = {
    val threadId = Thread.currentThread().getId

    this.synchronized {
      val newAccess  = System.currentTimeMillis()
      val waitLast   = newAccess - lastAccess
      val tmp        = delta - waitLast
      val actualWait = if (tmp < 0) 0 else tmp

      logger.info(s"threadId: $threadId - lastAccess: $lastAccess - newAccess: $newAccess - waitLast: $waitLast - actualWait: $actualWait - token: $token")

      val t = body

//      if (waitLast <= delta) Thread.sleep(actualWait)
      Thread.sleep(actualWait)

      lastAccess = System.currentTimeMillis()
//      lastAccess = newAccess

      t
    }
  }
}
