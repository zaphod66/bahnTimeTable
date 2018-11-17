package controllers

import javax.inject._

import play.api.mvc._
import com.softwaremill.sttp._
import model.TableEntry
import org.joda.time.DateTime

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  implicit val backend = HttpURLConnectionBackend()

  private def decorateDateString(str: String): String = {
    if (str.length != 10) str
    else {
      val yea = str.substring(0, 2)
      val mon = str.substring(2, 4)
      val day = str.substring(4, 6)
      val hou = str.substring(6, 8)
      val min = str.substring(8, 10)

//    s"$yea.$mon.$day - $hou:$min"
      s"$hou:$min"
    }
  }

  private def lessThan(e1: TableEntry, e2: TableEntry): Boolean = {
    val arr1 = e1.arrival
    val dep1 = e1.departure
    val arr2 = e2.arrival
    val dep2 = e2.departure

    val time1 = if (arr1 != "-") arr1 else dep1
    val time2 = if (arr2 != "-") arr2 else dep2

    time1 < time2
  }

  def index = Action {
    val date = DateTime.now()
    val dateStr = date.toString("yyMMdd")
    val hourStr = date.getHourOfDay

    val HH_Dammtor         = 8002548
    val HH_Elbgaustr       = 8001739
    val HH_Jungfernstieg   = 8003137
    val HH_Landungsbrücken = 8003518
    val HH_HBF_Fern        = 8002549
    val HH_HBF_SBahn       = 8098549
    val BR_Brieselang      = 8013472

    val req1 = sttp.header("Accept", "application/xml").header("Authorization", "Bearer 67332c908af9458ed8584e4f9fa7c641").get(uri"https://api.deutschebahn.com/timetables/v1/fchg/$HH_Elbgaustr")
    val req2 = sttp.header("Accept", "application/xml").header("Authorization", "Bearer 67332c908af9458ed8584e4f9fa7c641").get(uri"https://api.deutschebahn.com/timetables/v1/rchg/$HH_Elbgaustr")
    val req3 = sttp.header("Accept", "application/xml").header("Authorization", "Bearer 67332c908af9458ed8584e4f9fa7c641").get(uri"https://api.deutschebahn.com/timetables/v1/plan/$HH_Elbgaustr/$dateStr/$hourStr")

    val res = req3.send()

    val resStr = res.unsafeBody
    val resXml = scala.xml.XML.loadString(resStr)

    val station = resXml.attributes("station").text
    println(s"Station: $station")

    val attrMap = resXml.attributes.asAttrMap
    println(s"Attributes: $attrMap")

    val items = resXml \\ "s"
    println("-------------------")
    println(resXml.toString)
    println("-------------------")
    items foreach { s =>  println(s"---\n$s\n${s \\ "ar"} - ${s \\ "dp"}\n---") }
    println("-------------------")

    val entries = items map { item =>
      val tl  = item \\ "tl"
      val ar = item \\ "ar"
      val dp = item \\ "dp"

      val an = ar.aggregate("-")((_, n) => n.attributes.asAttrMap.getOrElse("pt", "-"), _ + _)
      val dn = dp.aggregate("-")((_, n) => n.attributes.asAttrMap.getOrElse("pt", "-"), _ + _)

      val ln =
        if (ar.nonEmpty)
          ar.head.attributes.asAttrMap.getOrElse("l", "-")
        else if (dp.nonEmpty)
          dp.head.attributes.asAttrMap.getOrElse("l", "-")
        else
          "-"

      val ca =
        if (tl.nonEmpty)
          tl.head.attributes.asAttrMap.getOrElse("c", "-")
        else "-"

      val n =
        if (tl.nonEmpty)
          tl.head.attributes.asAttrMap.getOrElse("n", ".")
        else "."

      val pptha = ar.aggregate("-")((_, n) => n.attributes.asAttrMap.getOrElse("ppth", "-"), _ + _)
      val ppthd = dp.aggregate("-")((_, n) => n.attributes.asAttrMap.getOrElse("ppth", "-"), _ + _)

      val depa = pptha.split('|').headOption.getOrElse("-")
      val dest = ppthd.split('|').lastOption.getOrElse("-")

      val line = if (ca == "S") s"$ca-$ln"
      else if (ca.startsWith("IC")) s"$ca $n"
      else if (ca.startsWith("R")) s"$ca$ln"
      else s"$ln"

      TableEntry(line, decorateDateString(an), decorateDateString(dn), depa, dest)
    }

    Ok(views.html.index(station, entries.sortWith(lessThan)))
  }
}
