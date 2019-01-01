package controllers

import javax.inject._

import play.api.mvc._
import com.softwaremill.sttp._
import model.TableEntry
import org.joda.time.DateTime

@Singleton
class BahnController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  implicit val backend = HttpURLConnectionBackend()

  private def decorateDateString(str: String): String = {
    if (str.length != 10) str
    else {
      //      val yea = str.substring(0, 2)
      //      val mon = str.substring(2, 4)
      //      val day = str.substring(4, 6)
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

  private def printlnBody(title: String, resStr: String): Unit = {
    val resXml = scala.xml.XML.loadString(resStr)

    println("---------------------------")
    println(title)
    println("---------------------------")
    println(resXml.toString())
    println("---------------------------")
  }

  private def getStation(eva: Int): String = {
    val date = DateTime.now()
    val dateStr = date.toString("yyMMdd")
    val hourStr = date.getHourOfDay

    // https://api.deutschebahn.com/timetables/v1/station/8003518
    val req = sttp.header("Accept", "application/xml").header("Authorization", "Bearer 67332c908af9458ed8584e4f9fa7c641").get(uri"https://api.deutschebahn.com/timetables/v1/station/$eva")
    val res = req.send()

    val resStr = res.unsafeBody
    val resXml = scala.xml.XML.loadString(resStr)

    printlnBody("getStattion", resStr)

    val t1 = resXml \\ "station"
    val t2 = t1 \@ "name"

    t2
  }

  private def getFullChanges(eva: Int): List[TableEntry] = {
    val fchgReq = sttp.header("Accept", "application/xml").header("Authorization", "Bearer 67332c908af9458ed8584e4f9fa7c641").get(uri"https://api.deutschebahn.com/timetables/v1/fchg/$eva")

    val fchgRes = fchgReq.send()
    val fchgStr = fchgRes.unsafeBody

    printlnBody("fchg", fchgStr)

    List.empty[TableEntry]
  }

  private def getEntries(eva: Int): List[TableEntry] = {
    val date = DateTime.now()
    val dateStr = date.toString("yyMMdd")
    val hourStr = date.getHourOfDay

    val planReq = sttp.header("Accept", "application/xml").header("Authorization", "Bearer 67332c908af9458ed8584e4f9fa7c641").get(uri"https://api.deutschebahn.com/timetables/v1/plan/$eva/$dateStr/$hourStr")
    val planRes = planReq.send()
    val planStr = planRes.unsafeBody

    printlnBody("timetable", planStr)

    val resXml = scala.xml.XML.loadString(planStr)

    val attrMap = resXml.attributes.asAttrMap
    println(s"Attributes: $attrMap")

    val items = resXml \\ "s"

    items foreach { s => println(s"-- s --\n$s\n----\n${s \\ "ar"}\n-\n${s \\ "dp"}\n-------") }

    val entries = items map { item =>
      val tl = item \\ "tl"
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

      val pp = if (ar.nonEmpty)
        ar.head.attributes.asAttrMap.getOrElse("pp", "-")
      else if (dp.nonEmpty)
        dp.head.attributes.asAttrMap.getOrElse("pp", "-")
      else
        "-"

      val pptha = ar.aggregate("-")((_, n) => n.attributes.asAttrMap.getOrElse("ppth", "-"), _ + _)
      val ppthd = dp.aggregate("-")((_, n) => n.attributes.asAttrMap.getOrElse("ppth", "-"), _ + _)

      val depa = pptha.split('|').headOption.getOrElse("-")
      val dest = ppthd.split('|').lastOption.getOrElse("-")

      val line = if (ca == "S") s"$ca-$ln"
      else if (ca.startsWith("IC") || ca.startsWith("NJ") || ca.startsWith("EC")) s"$ca $n"
      else if (ca.startsWith("R")) s"$ca$ln"
      else s"$ln"

      TableEntry(line, pp, decorateDateString(an), decorateDateString(dn), depa, dest)
    }

    entries.toList
  }

  def timeTableServerEva(eva: Int) = Action {
    val station = getStation(eva)
    val entries = getEntries(eva)

    getFullChanges(eva)

    Ok(views.html.index(station, entries.sortWith(lessThan)))
  }

  def timeTableServer = Action {
    val HH_Dammtor = 8002548
    val HH_Elbgaustr = 8001739
    val HH_Jungfernstieg = 8003137
    val HH_Landungsbrücken = 8003518
    val HH_HBF_Fern = 8002549
    val HH_HBF_SBahn = 8098549
    val BR_Brieselang = 8013472
    val MK_Cammin = 8011307
    val SH_Pinneberg = 8004819

    val eva = HH_Landungsbrücken

    val station = getStation(eva)
    val entries = getEntries(eva)

    Ok(views.html.index(station, entries.sortWith(lessThan)))
  }

  def timeTable = Action {
    import scala.concurrent.ExecutionContext.Implicits.global

    Ok.sendFile(new java.io.File("public/bahnTimeTable.html"))
  }

}
