package utils

object Throttler {
  def throttle(): Unit = {
    this.synchronized(Thread.sleep(3000)) // 20 requests per minute
  }
}
