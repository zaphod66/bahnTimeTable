package utils

import cats.effect.IO

object DelayedFuture {
  import scala.concurrent.{ Future, Promise, blocking }
  import java.util.{Timer, TimerTask}
  import scala.concurrent.duration.FiniteDuration
  import scala.util.Try

  val timer   = new Timer(true)

  def toDo(i: Int, start: Long  = System.currentTimeMillis): Unit = { blocking { Thread.sleep(1000) }; val stop = System.currentTimeMillis; println(s"$i : ${Thread.currentThread} - ${stop - start}ms") }

  private def makeFuture[T](delay: Long)(body: => T): Future[T] = {
    val promise = Promise[T]()

    timer.schedule(new TimerTask {
      override def run(): Unit = promise.complete(Try { body })
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

object CatsDelayedFuture {
  import scala.concurrent.ExecutionContext
  import scala.concurrent.duration._

  import cats.effect.concurrent.Semaphore

  implicit val ctx = IO.contextShift(ExecutionContext.global)
  implicit val timer = IO.timer(ExecutionContext.global)

  val dfM = IO(DelayedFuture(3.seconds)(println("Hello")))
  val sem = Semaphore[IO](2)

  val f = for {
    _ <- sem.map(_.acquire)
    _ <- dfM
    _ <- sem.map(_.release)
  } yield()

  f.unsafeRunSync()
}
