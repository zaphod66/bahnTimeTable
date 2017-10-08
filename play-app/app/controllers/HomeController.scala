package controllers

import javax.inject._
import play.api.mvc._

import com.softwaremill.sttp._

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  implicit val backend = HttpURLConnectionBackend()

  /**
   * Create an Action to render an HTML page with a welcome message.
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index = Action {
//  val req = sttp.header("Accept", "application/xml").header("Authorization", "Bearer 67332c908af9458ed8584e4f9fa7c641").get(uri"https://api.deutschebahn.com/timetables/v1/fchg/8001739")
    val req = sttp.header("Accept", "application/xml").header("Authorization", "Bearer 67332c908af9458ed8584e4f9fa7c641").get(uri"https://api.deutschebahn.com/timetables/v1/rchg/8001739")

    val res = req.send()

    Ok(views.html.index(res.unsafeBody))
  }

}
