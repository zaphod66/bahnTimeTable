package controllers

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDateTime, ZoneId}

import cn.playscala.mongo.Mongo
import com.softwaremill.sttp._
import com.typesafe.scalalogging.StrictLogging
import javax.inject._
import model.{DBDs100Entry, Ds100Entry, StationEntry, TableEntry}
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, Writes}
import play.api.mvc._
import utils.{LoggingSttpBackend, ThrottlingSttpBackend}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try
import scala.language.higherKinds

@Singleton
class BahnController @Inject()(cc: ControllerComponents, mongo: Mongo)
  extends AbstractController(cc) with StrictLogging {


  val standardBackend = HttpURLConnectionBackend()
  val loggingBackend  = new LoggingSttpBackend[Id, Nothing](standardBackend)
  val throttlingBackend = new ThrottlingSttpBackend[Id, Nothing](loggingBackend)

  private implicit val backend: SttpBackend[Id, Nothing] = throttlingBackend

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

  private def instant2LocalDateTime(instant: Instant): LocalDateTime = {
    LocalDateTime.ofInstant(instant, ZoneId.systemDefault().normalized())
  }

  private def dateString2LocalDateTime(str: String): Option[LocalDateTime] = {
    val instant = dateString2Instant(str)

    instant map { i => LocalDateTime.ofInstant(i, ZoneId.of("UTC")) }
  }

  //  private def minutesBetween(t1: Instant, t2: Instant): Long = {
  //    import java.time.Duration
  //
  //    val d = Duration.between(t1, t2)
  //
  //    d.toMinutes
  //  }

  private def lessThan(e1: TableEntry, e2: TableEntry): Boolean = {
    val arr1 = e1.arrival
    val dep1 = e1.departure
    val arr2 = e2.arrival
    val dep2 = e2.departure

    val time1 = if (arr1.isDefined) arr1 else dep1
    val time2 = if (arr2.isDefined) arr2 else dep2

    (time1, time2) match {
      case (Some(t1), Some(t2)) => t1.isBefore(t2)
      case (Some(_), None) => false
      case (None, Some(_)) => true
      case (None, None) => false
    }
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

//    Throttler.throttle()

    val req = sttp.header("Accept", "application/xml").header("Authorization", "Bearer 8aa98ee641a28d95cddf612756cf1abd").get(uri"https://api.deutschebahn.com/timetables/v1/station/$ds100")
    val res = req.send()

    logger.info(s"getStationDs100($ds100)")
    logger.info(s"${req.toCurl}")

    try {
      val resStr = res.unsafeBody
      val resXml = scala.xml.XML.loadString(resStr)

      val t1 = resXml \\ "station"
      val t2 = t1 \@ "name"
      val t3 = t1 \@ "eva"

      if (t3 == "") {
        logger.info(s"getStationDs100($ds100): Not found")
        None
      } else {
        logger.info(s"getStationDs100($ds100): $t1 -> ($t2,$t3)")
        Option((t2, t3.toInt))
      }
    } catch {
      case e: Exception => logger.error(s"getStationDs100($ds100) fail: ${e.getMessage}"); None
    }
  }

  private def getStationEva(eva: Int): String = {
    // https://api.deutschebahn.com/timetables/v1/station/8003518
    val req = sttp.header("Accept", "application/xml").header("Authorization", "Bearer 8aa98ee641a28d95cddf612756cf1abd").get(uri"https://api.deutschebahn.com/timetables/v1/station/$eva")
    val res = req.send()

    logger.info(s"getStationEva($eva)")
    logger.info(s"${req.toCurl}")

    try {
      val resStr = res.unsafeBody
      val resXml = scala.xml.XML.loadString(resStr)

      //      printlnBody(s"getStationEva($eva)", resStr)

      val t1 = resXml \\ "station"
      val t2 = t1 \@ "name"

      t2
    } catch {
      case _: NoSuchElementException => "<unknown>"
    }
  }

  private def getBetriebsstellen(name: String): List[StationEntry] = {
    // https://api.deutschebahn.com/betriebsstellen/v1/betriebsstellen?name=altona
    val reqHeader = sttp.header("Accept", "application/xml").header("Authorization", "Bearer 8aa98ee641a28d95cddf612756cf1abd")
    val req = reqHeader.get(uri"https://api.deutschebahn.com/betriebsstellen/v1/betriebsstellen?name=$name")

    val res = req.send()
    try {
      val str = res.unsafeBody

      import play.api.libs.json._

      val json = Json.parse(str)
      val jsonArr = json.asInstanceOf[JsArray]
      val jsonSeq = jsonArr.value

      val stations = jsonSeq map { js =>
        val lName = (js \ "name").as[String]
        val sName = (js \ "short").as[String]
        val abbr = (js \ "abbrev").as[String]
        val tpe = (js \ "type").as[String]
        val status = (js \ "status").asOpt[String].getOrElse("in use")

        StationEntry(longName = lName, shortName = sName, ds100 = abbr, tpe = tpe, status = status)
      }

      val stationTypes = Set[String]("Bf", "Bft", "Hp", "NE-Bf", "NE-Bft"/*, "Bush"*/)

      def cond(s: StationEntry): Boolean = stationTypes.contains(s.tpe) && s.status == "in use"

      logger.info(s"getBetriebsstellen($name)")
      logger.info(s"${req.toCurl}")

//      stations foreach println
//      println("---------------------------")
//      stations.filter(cond) foreach println
//      println("---------------------------")

      stations.filter(cond).toList
    } catch {
      case e: NoSuchElementException =>
        logger.error(s"getBetriebsstellen($name) failed: ${e.getMessage}")
        List.empty[StationEntry]
    }
  }

  private def getFullChanges(eva: Int): List[TableEntry] = {
    val fchgReq = sttp.header("Accept", "application/xml").header("Authorization", "Bearer 8aa98ee641a28d95cddf612756cf1abd").get(uri"https://api.deutschebahn.com/timetables/v1/fchg/$eva")

    logger.info(s"getFullChanges($eva)")
    logger.info(s"${fchgReq.toCurl}")

    val fchgRes = fchgReq.send()
    try {
      val fchgStr = fchgRes.unsafeBody
      val fchgXml = scala.xml.XML.loadString(fchgStr)

//      val attrMap = fchgXml.attributes.asAttrMap
//      println(s"Attributes: $attrMap")

      val items = fchgXml \\ "s"

      val resMap = items.map(s => s.attribute("id") -> s).toMap
        .filter { case (k, _) => k.isDefined }
        .map { case (k, v) => (k.get, v) }

      val resMap2 = resMap.mapValues { n =>
        val ar = n \\ "ar"
        val dp = n \\ "dp"

        val arLDT = dateString2LocalDateTime(ar.\@("ct"))
        val dpLDT = dateString2LocalDateTime(dp.\@("ct"))

        (arLDT, dpLDT)
      }.filter { case (k, _) => k.nonEmpty }.map { case (k, v) => (k.head.toString, v) }

      resMap2.map { case (k, v) => TableEntry(k, "", "", None, None, "", "", v._1, v._2) }.toList
    } catch {
      case e: NoSuchElementException => logger.error(s"Error in getFullChanges($eva): ${e.getMessage}"); List.empty[TableEntry]
    }
  }

  private def getEntries(eva: Int): List[TableEntry] = {
    val instant = Instant.now() //.minusSeconds(3600)
    val dt = instant2LocalDateTime(instant)
    val dfDate = DateTimeFormatter.ofPattern("yyMMdd")
    val dfHour = DateTimeFormatter.ofPattern("HH")

    val dateStr = dt.format(dfDate)
    val hourStr = dt.format(dfHour)


    val planReq = sttp.header("Accept", "application/xml").header("Authorization", "Bearer 8aa98ee641a28d95cddf612756cf1abd").get(uri"https://api.deutschebahn.com/timetables/v1/plan/$eva/$dateStr/$hourStr")
    val planRes = planReq.send()

    logger.info(s"getEntries($eva)")
    logger.info(s"${planReq.toCurl}")

    try {
      val planStr = planRes.unsafeBody
      val resXml = scala.xml.XML.loadString(planStr)

      //      println(s"""======> ${resXml.attributes.asAttrMap}""")

      val station = resXml.attribute("station").map(_.toString).getOrElse("-")

      val items = resXml \\ "s"

      //      val resMap = items.map(s => s.attribute("id") -> s).toMap
      //        .filter { case (k, _) => k.isDefined }
      //        .map { case (k,v) => (k.get.toString, v) }
      //
      //      resMap foreach println

      //      items foreach { item =>
      //        println("------------------")
      //        println(item)
      //        println("------------------")
      //      }

      val entries = items map { item =>
        val tl = item \\ "tl"
        val ar = item \\ "ar"
        val dp = item \\ "dp"

        val an = ar.aggregate("-")((_, n) => n.attributes.asAttrMap.getOrElse("pt", "-"), _ + _)
        val dn = dp.aggregate("-")((_, n) => n.attributes.asAttrMap.getOrElse("pt", "-"), _ + _)

        val arLDT = dateString2LocalDateTime(an)
        val dpLDT = dateString2LocalDateTime(dn)

        //        val ln =
        //          if (ar.nonEmpty)
        //            ar.head.attributes.asAttrMap.getOrElse("l", "+")
        //          else if (dp.nonEmpty)
        //            dp.head.attributes.asAttrMap.getOrElse("l", "++")
        //          else
        //            "+++"

        val lnM = if (ar.nonEmpty)
          ar.head.attributes.asAttrMap.get("l")
        else if (dp.nonEmpty)
          dp.head.attributes.asAttrMap.get("l")
        else
          Option.empty[String]

        val ln = lnM.getOrElse("+")

        val ca =
          if (tl.nonEmpty)
            tl.head.attributes.asAttrMap.getOrElse("c", "*")
          else "**"

        val n =
          if (tl.nonEmpty)
            tl.head.attributes.asAttrMap.getOrElse("n", "#")
          else "##"

        val pp = if (ar.nonEmpty)
          ar.head.attributes.asAttrMap.getOrElse("pp", "$")
        else if (dp.nonEmpty)
          dp.head.attributes.asAttrMap.getOrElse("pp", "$$")
        else
          "$$$"

        val pptha = ar.aggregate(station)((_, n) => n.attributes.asAttrMap.getOrElse("ppth", "%"), _ + _)
        val ppthd = dp.aggregate(station)((_, n) => n.attributes.asAttrMap.getOrElse("ppth", "%%"), _ + _)

        val depa = pptha.split('|').headOption.getOrElse(station)
        val dest = ppthd.split('|').lastOption.getOrElse(station)

        //        val line = if (ca == "S") s"$ca-$ln"
        //        else if (ca =="IC" || ca =="ICE" || ca =="NJ" || ca == "EC" || ca =="IRE") s"$ca $n"
        //        else s"$ca $ln ($n)"

        val line = if (ca == "S")
          s"""$ca${lnM.getOrElse("?")}"""
        else if (lnM.isEmpty) s"$ca $n"
        else s"$ca $ln ($n)"

        val id = item.attribute("id").get.head.buildString(true)
        TableEntry(id, line, pp, arLDT, dpLDT, depa, dest)
      }

      entries.toList
    } catch {
      case e: NoSuchElementException => /*e.printStackTrace();*/ logger.error(s"Error in getEntries($eva) - ${e.getMessage}"); List.empty[TableEntry]
    }
  }

  private def timeTableEntries(eva: Int): List[TableEntry] = {
//    val station = getStationEva(eva)
    val entries = getEntries(eva)
    val fchgs = getFullChanges(eva)

    val entriesMap = entries.map(t => t.id -> t).toMap
    val fchgsMap = fchgs.map(t => t.id -> t).toMap

    val ek = entriesMap.keySet
    val fk = fchgsMap.filter { case (k, _) => ek.contains(k) }

    val entries2 = entries map { t =>
      val delay = fk.get(t.id)

      delay.fold(t)(_ => t.copy(arDelay = delay.get.arDelay, dpDelay = delay.get.dpDelay))
    }

    entries2.sortWith(lessThan)
  }

  def timeTableServerEva(eva: Int) = Action {

    logger.info(s"timeTableServerEva($eva)")

    val station = getStationEva(eva)
    val entries = timeTableEntries(eva)

    Ok(views.html.index(station, entries.sortWith(lessThan)))
  }

  def timeTableServerJson(eva: Int) = Action {
    implicit val entriesWrites = Json.writes[TableEntry]

    val entries = timeTableEntries(eva)

    Ok(Json.toJson(entries))
  }

  def timeTable = Action { _ =>
//    import scala.concurrent.ExecutionContext.Implicits.global

    Ok.sendFile(new java.io.File("public/bahnTimeTable.html"))
  }

  def station(ds100: String) = Action {

    val (name, eva) = getStationDs100(ds100).getOrElse(("<unknown>", 0))

    Ok(views.html.welcome(s"$ds100: ($name - $eva)"))
  }

  def betriebstellen(name: String) = Action {

    import java.nio.charset.{StandardCharsets => SC}

    import play.utils.UriEncoding

    val decodedName = UriEncoding.decodePath(name, SC.UTF_8)

    val stations = getBetriebsstellen(decodedName)
    val ds100s = stations map (_.ds100)

    val stationsRaw = ds100s map {
      getStationDs100
    }
    val stationsList = stationsRaw.flatten

    println("-----------------")
    stationsList foreach println
    println("-----------------")

    Ok(views.html.stations(stationsList))
  }

  def betriebstellenJson(name: String) = Action {
    case class NameDs100(name: String, ds100: String)

    implicit val nameDs100Writes: Writes[NameDs100] =
      ((JsPath \ "name").write[String] and (JsPath \ "ds100").write[String]) (unlift(NameDs100.unapply))

    import java.nio.charset.{StandardCharsets => SC}
    import play.utils.UriEncoding

    val decodedName = UriEncoding.decodePath(name, SC.UTF_8)

    val stations = getBetriebsstellen(decodedName)

    val pairs = stations map (s => NameDs100(s"${s.longName}", s.ds100))

    Ok(Json.toJson(pairs))
  }

  implicit def ec: ExecutionContext = cc.executionContext

  def findByDs100(ds100: String): Future[Option[Ds100Entry]] = {
    val f = mongo.find[DBDs100Entry](Json.obj("ds100" -> ds100)).first

    f.map{ dbfM =>
      dbfM.flatMap { dbf =>
        Option(Ds100Entry(dbf.ds100, dbf.eva, dbf.stationName, dbf.found))
      }
    }
  }

  def storeDs100(ds100: String, name: String, eva: Int): Future[Void] = {

    logger.info(s"storeDs100($ds100, $name, $eva)")

    mongo.insertOne[DBDs100Entry](DBDs100Entry(_id = ds100, ds100 = ds100, eva = eva, stationName = name, found = true))

  }

  def storeDs100Failed(ds100: String): Future[Void] = {

    logger.info(s"storeDs100Failed($ds100)")

    mongo.insertOne[DBDs100Entry](DBDs100Entry(_id = ds100, ds100 = ds100, eva = 0, stationName = "", found = false))
  }

  def getStationDs100Json(ds100: String) = Action {
    case class NameEva(name: String, eva: Int)

    implicit val nameDs100Writes: Writes[NameEva] =
      ((JsPath \ "name").write[String] and (JsPath \ "eva").write[Int]) (unlift(NameEva.unapply))

    import java.nio.charset.{StandardCharsets => SC}
    import play.utils.UriEncoding

    val decodedDs100 = UriEncoding.decodePath(ds100, SC.UTF_8)

    val foundStationM = findByDs100(decodedDs100)
    val foundStation  = Await.result(foundStationM, Duration.Inf)

    val nameEva = foundStation.fold {
      val neM = getStationDs100(decodedDs100).map((NameEva.apply _).tupled)

      neM.fold {
        storeDs100Failed(decodedDs100)
      } {
        ne => storeDs100(decodedDs100, ne.name, ne.eva)
      }

      neM
    } (s => if (s.found) Option(NameEva(s.stationName, s.eva)) else Option.empty[NameEva])

    Ok(Json.toJson(nameEva))
  }
}
