package utils

object DelayedFuture {
  import scala.concurrent.{ Future, Promise }
  import java.util.{Timer, TimerTask}
  import scala.concurrent.duration.FiniteDuration
  import scala.util.Try

  val timer   = new Timer(true)

  private def makeFuture[T](delay: Long)(thunk: => T): Future[T] = {
    val promise = Promise[T]()

    timer.schedule(new TimerTask {
      override def run(): Unit = promise.complete(Try { thunk })
    }, delay)

    promise.future
  }

  def apply[T](delay: Long)(body: => T): Future[T] = {
    makeFuture(delay)(body)
  }

  def apply[T](duration: FiniteDuration)(body: => T): Future[T] = {
    makeFuture(duration.toMillis)(body)
  }
}
