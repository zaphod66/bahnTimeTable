package controllers

import javax.inject.Inject

import play.api.mvc.{AbstractController, ControllerComponents}

class HtmlGameController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  def htmlgame = Action {

    import scala.concurrent.ExecutionContext.Implicits.global

    Ok.sendFile(new java.io.File("public/htmlgame.html"))
  }

}
