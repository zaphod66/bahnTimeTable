package utils

object Throttler {
  private val delta      = 3000L  // in millis
  private var lastAccess = System.currentTimeMillis() - delta
  private var lastInc    = 0L
  private var token      = 20
  def throttle(): Unit = {
    this.synchronized {
      val newAccess = System.currentTimeMillis()
      val duration  = newAccess - lastAccess
      println(s"${this.getClass} - token: $token  duration: $duration")

      Thread.sleep(delta)
//      if (token > 0) {
//        token = token - 1
//      } else {
//        Thread.sleep(delta)
//      }
      lastAccess = newAccess
    } // 20 requests per minute
  }
}
