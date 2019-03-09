package controllers

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDateTime, ZoneId}

import javax.inject._
import play.api.mvc._
import com.softwaremill.sttp._
import model.{StationEntry, TableEntry}

import scala.util.Try

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

  private def dateString2Iso(str: String): String = {
    val yea = str.substring(0, 2)
    val mon = str.substring(2, 4)
    val day = str.substring(4, 6)
    val hou = str.substring(6, 8)
    val min = str.substring(8, 10)

    s"20$yea-$mon-${day}T$hou:$min:00Z"
  }

  private def dateString2Instant(str: String): Option[Instant] = {
    Try {
      Instant.parse(dateString2Iso(str))
    }.toOption
  }

  private def minutesBetween(t1: Instant, t2: Instant): Long = {
    import java.time.Duration

    val d = Duration.between(t1, t2)

    d.toMinutes
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

  private def getStationDs100(ds100: String): Option[(String, Int)] = {
    // https://api.deutschebahn.com/timetables/v1/station/8003518
    val req = sttp.header("Accept", "application/xml").header("Authorization", "Bearer 67332c908af9458ed8584e4f9fa7c641").get(uri"https://api.deutschebahn.com/timetables/v1/station/$ds100")
    val res = req.send()

//    println("===========================")
//    println(s"getStationDs100($ds100)")
//    println("===========================")

    try {
      val resStr = res.unsafeBody
      val resXml = scala.xml.XML.loadString(resStr)

//      printlnBody(s"getStationDs100($ds100)", resStr)

      val t1 = resXml \\ "station"
      val t2 = t1 \@ "name"
      val t3 = t1 \@ "eva"
//      val t4 = try {
//        t3.toInt
//      } catch {
//        case _: NumberFormatException => 0
//      }

      Option((t2, t3.toInt))
    } catch { case _: Exception => None }
  }

  private def getStationEva(eva: Int): String = {
    // https://api.deutschebahn.com/timetables/v1/station/8003518
    val req = sttp.header("Accept", "application/xml").header("Authorization", "Bearer 67332c908af9458ed8584e4f9fa7c641").get(uri"https://api.deutschebahn.com/timetables/v1/station/$eva")
    val res = req.send()

    try {
      val resStr = res.unsafeBody
      val resXml = scala.xml.XML.loadString(resStr)

      printlnBody(s"getStationEva($eva)", resStr)

      val t1 = resXml \\ "station"
      val t2 = t1 \@ "name"

      t2
    } catch {
      case _: NoSuchElementException => "unknown"
    }
  }

  private def getBetriebsstellen(name: String): List[StationEntry] = {
    // https://api.deutschebahn.com/betriebsstellen/v1/betriebsstellen?name=altona
    val reqHeader = sttp.header("Accept", "application/xml").header("Authorization", "Bearer 67332c908af9458ed8584e4f9fa7c641")
    val req = reqHeader.get(uri"https://api.deutschebahn.com/betriebsstellen/v1/betriebsstellen?name=$name")
    val res = req.send()
    try {
      val str = res.unsafeBody

      import play.api.libs.json._

      val json = Json.parse(str)
      val jsonArr = json.asInstanceOf[JsArray]
      val jsonSeq = jsonArr.value

      val stations = jsonSeq map { js =>
        val lName  = (js \ "name").as[String]
        val sName  = (js \ "short").as[String]
        val abbr   = (js \ "abbrev").as[String]
        val tpe    = (js \ "type").as[String]
        val status = (js \ "status").asOpt[String].getOrElse("in use")

        StationEntry(longName = lName, shortName = sName, ds100 = abbr, tpe = tpe, status = status)
      }

      def cond(s: StationEntry): Boolean = (s.tpe == "Bf" || s.tpe == "Bft" || s.tpe == "Hp") && s.status == "in use"

      println("---------------------------")
      println(s"getBetriebsstellen($name)")
      println("---------------------------")
      stations foreach println
      println("---------------------------")
      stations.filter(cond) foreach println
      println("---------------------------")

      stations.filter(cond).toList
    } catch {
      case _: NoSuchElementException => List.empty[StationEntry]
    }
  }

  private def getFullChanges(eva: Int): List[TableEntry] = {
    val fchgReq = sttp.header("Accept", "application/xml").header("Authorization", "Bearer 67332c908af9458ed8584e4f9fa7c641").get(uri"https://api.deutschebahn.com/timetables/v1/fchg/$eva")

    val fchgRes = fchgReq.send()
    try {
//      printlnBody(s"getFullChanges($eva)", fchgStr)
      val fchgStr = fchgRes.unsafeBody
      val fchgXml = scala.xml.XML.loadString(fchgStr)

      val attrMap = fchgXml.attributes.asAttrMap
      println(s"Attributes: $attrMap")

      val items = fchgXml \\ "s"

      val resMap = items.map(s => s.attribute("id") -> s).toMap
        .filter { case (k, _) => k.isDefined }
        .map { case (k,v) => (k.get, v) }

      val resMap2 = resMap.mapValues { n =>
        val ar = n \\ "ar"
        val dp = n \\ "dp"

        val arStr = decorateDateString(ar.\@("ct"))
        val dpStr = decorateDateString(dp.\@("ct"))

        (arStr, dpStr)
      }.filter { case (k, _) => k.nonEmpty }.map {case (k, v) => (k.head.toString, v)}

//      resMap2 foreach println

      resMap2.map { case (k, v) => TableEntry(k, "", "", "", "", "", "", v._1, v._2) }.toList
    } catch {
      case _: NoSuchElementException => println(s"Error in getFullChanges($eva)"); List.empty[TableEntry]
    }
  }

  private def getEntries(eva: Int): List[TableEntry] = {
    val instant = Instant.now()
    val dt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
    val dfDate = DateTimeFormatter.ofPattern("yyMMdd")
    val dfHour = DateTimeFormatter.ofPattern("hh")

    val dateStr = dt.format(dfDate)
    val hourStr = dt.format(dfHour)

    val planReq = sttp.header("Accept", "application/xml").header("Authorization", "Bearer 67332c908af9458ed8584e4f9fa7c641").get(uri"https://api.deutschebahn.com/timetables/v1/plan/$eva/$dateStr/$hourStr")
    val planRes = planReq.send()

    try {
      val planStr = planRes.unsafeBody

//      printlnBody(s"getEntries($eva)", planStr)

      val resXml = scala.xml.XML.loadString(planStr)

      val attrMap = resXml.attributes.asAttrMap
      println(s"Attributes: $attrMap")

      val items = resXml \\ "s"

//      items foreach { s => println(s"""id - ${s.attribute("id")} """) }

      val resMap = items.map(s => s.attribute("id") -> s).toMap
        .filter { case (k, _) => k.isDefined }
        .map { case (k,v) => (k.get.toString, v) }

//      resMap foreach println

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
        else if (ca.startsWith("IC") || ca.startsWith("NJ") || ca.startsWith("EC") || ca.startsWith("RB")) s"$ca $n"
        else if (ca.startsWith("R")) s"$ca$ln"
        else s"$ln"

//        val itemMap = item.attributes.asAttrMap
//        itemMap foreach { kv => println(s"${kv._1} -> ${kv._2}") }
//        val tlMap = tl.head.attributes.asAttrMap
//        tlMap foreach { kv => println(s"${kv._1} -> ${kv._2}") }

        val id = item.attribute("id").get.head.buildString(true)
        TableEntry(id, line, pp, decorateDateString(an), decorateDateString(dn), depa, dest)
      }

      entries.toList
    } catch {
      case e: NoSuchElementException => /*e.printStackTrace();*/ println(s"Error in getEntries($eva) - $e}"); List.empty[TableEntry]
    }
  }

  def timeTableServerEva(eva: Int) = Action {
    val station = getStationEva(eva)
    val entries = getEntries(eva)
    val fchgs   = getFullChanges(eva)

    val entriesMap = entries.map(t => t.id -> t).toMap
    val fchgsMap   = fchgs.map(t => t.id -> t).toMap

    val ek = entriesMap.keySet
    val fk = fchgsMap.filter { case (k, _) => ek.contains(k) }

    val entries2 = entries map { t =>
      val delay = fk.get(t.id)

      //    delay.fold(t)(t => t.copy(arDelay = delay.get.arDelay, dpDelay = delay.get.dpDelay))
      delay.fold(t)(_ => t.copy(arDelay = delay.get.arDelay, dpDelay = delay.get.dpDelay))
    }

//    entries foreach println
//    println("-------")
//    fk.values foreach println
//    println("-------")
    entries2 foreach println

    Ok(views.html.index(station, entries2.sortWith(lessThan)))
  }

  def timeTableServer = Action {
    // Abkürzungen der Betriebsstellen
    // http://www.bahnseite.de/DS100/DS100_main.html
    val HH_Dammtor = 8002548
    val HH_Elbgaustr = 8001739
    val HH_Jungfernstieg = 8003137
    val HH_Landungsbrücken = 8003518
    val HH_HBF_Fern = 8002549
    val HH_HBF_SBahn = 8098549
    val BR_Brieselang = 8013472
    val MK_Cammin = 8011307
    val SH_Pinneberg = 8004819
    val HL_HBF = 8000237

    val eva = HH_Landungsbrücken

    val station = getStationEva(eva)
    val entries = getEntries(eva)

    Ok(views.html.index(station, entries.sortWith(lessThan)))
  }

  def timeTable = Action { request =>
    import scala.concurrent.ExecutionContext.Implicits.global

    val b = request.body
    Ok.sendFile(new java.io.File("public/bahnTimeTable.html"))
  }

  def station(ds100: String) = Action {

    val (name, eva) = getStationDs100(ds100).getOrElse((" unknown ", 0))

    Ok(views.html.welcome(s"$ds100: ($name - $eva)"))
  }

  def betriebstellen(name: String) = Action {

    val stations = getBetriebsstellen(name)
    val ds100s   = stations map (_.ds100)

    val tt = ds100s map getStationDs100

    tt.flatten foreach println

    Ok
  }
}
