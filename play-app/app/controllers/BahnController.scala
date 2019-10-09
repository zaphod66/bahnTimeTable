package controllers

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDateTime, ZoneId}

import cats.effect.{ContextShift, IO, Timer}
import cats.effect.concurrent.Semaphore
import cn.playscala.mongo.Mongo
import com.softwaremill.sttp._
import com.typesafe.scalalogging.StrictLogging
import javax.inject._
import model.{DBDs100Entry, Ds100Entry, StationEntry, TableEntry}
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsObject, JsPath, JsValue, Json, Writes}
import play.api.mvc._
import utils.{IOSttpBackend, IOThrottlingSttpBackend, LoggingSttpBackend, ThrottlingSttpBackend}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try
import scala.language.higherKinds

@Singleton
class BahnController @Inject()(cc: ControllerComponents, mongo: Mongo)
  extends AbstractController(cc) with StrictLogging {

  implicit val ctx: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

  val standardBackend = HttpURLConnectionBackend()
  val loggingBackend  = new LoggingSttpBackend[Id, Nothing](standardBackend)
  val ioBackend = new IOSttpBackend(loggingBackend)
  val ioThrottlingBackend = new IOThrottlingSttpBackend(ioBackend)

//  val throttlingBackend = new ThrottlingSttpBackend[Id, Nothing](loggingBackend)
//  private implicit val backend: SttpBackend[Id, Nothing] = throttlingBackend

  private implicit val backend: SttpBackend[IO, Nothing] = ioThrottlingBackend

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

//  private def printlnBody(title: String, resStr: String): Unit = {
//    val resXml = scala.xml.XML.loadString(resStr)
//
//    println("---------------------------")
//    println(title)
//    println("---------------------------")
//    println(resXml.toString())
//    println("---------------------------")
//  }

  private def getStationDs100(ds100: String): Option[(String, Int)] = {

    def doIt(res: Id[Response[String]]): Option[(String, StatusCode)] = {
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

    logger.info(s"getStationDs100($ds100)")

    val req = sttp.header("Accept", "application/xml").header("Authorization", "Bearer 8aa98ee641a28d95cddf612756cf1abd").get(uri"https://api.deutschebahn.com/timetables/v1/station/$ds100")

    req.send().map(doIt).unsafeRunSync()
  }

  private def getStationEva(eva: Int): Option[String] = {

    def doIt(res: Id[Response[String]]): Option[String] = {
      Try {
        val resStr = res.unsafeBody
        val resXml = scala.xml.XML.loadString(resStr)

        val t1 = resXml \\ "station"
        val t2 = t1 \@ "name"

        t2
      }.toOption
    }

    logger.info(s"getStationEva($eva)")

    // https://api.deutschebahn.com/timetables/v1/station/8003518
    val req = sttp.header("Accept", "application/xml").header("Authorization", "Bearer 8aa98ee641a28d95cddf612756cf1abd").get(uri"https://api.deutschebahn.com/timetables/v1/station/$eva")

    req.send().map(doIt).unsafeRunSync()
  }

  private def getBetriebsstellen(name: String): List[StationEntry] = {

    def doIt(res: Id[Response[String]]): List[StationEntry] =
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

        stations.filter(cond).toList
      } catch {
        case e: NoSuchElementException =>
          logger.error(s"getBetriebsstellen($name) failed:\n${e.getMessage}")

          val futureList = findByNamePattern(name)

          Await.result(futureList, Duration.Inf)
      }

    // https://api.deutschebahn.com/betriebsstellen/v1/betriebsstellen?name=altona
    val reqHeader = sttp.header("Accept", "application/xml").header("Authorization", "Bearer 8aa98ee641a28d95cddf612756cf1abd")
    val req = reqHeader.get(uri"https://api.deutschebahn.com/betriebsstellen/v1/betriebsstellen?name=$name")

    req.send().map(doIt).unsafeRunSync()
  }

  private def getFullChanges(eva: Int): List[TableEntry] = {
    def doIt(res: Id[Response[String]]): List[TableEntry] = {
      try {
        val fchgStr = res.unsafeBody
        val fchgXml = scala.xml.XML.loadString(fchgStr)

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

    val req = sttp.header("Accept", "application/xml").header("Authorization", "Bearer 8aa98ee641a28d95cddf612756cf1abd").get(uri"https://api.deutschebahn.com/timetables/v1/fchg/$eva")

    logger.info(s"getFullChanges($eva)")

    req.send().map(doIt).unsafeRunSync()
  }

  private def getEntries(eva: Int): List[TableEntry] = {
    val instant = Instant.now() //.minusSeconds(3600)
    val dt = instant2LocalDateTime(instant)
    val dfDate = DateTimeFormatter.ofPattern("yyMMdd")
    val dfHour = DateTimeFormatter.ofPattern("HH")

    val dateStr = dt.format(dfDate)
    val hourStr = dt.format(dfHour)

    logger.info(s"getEntries($eva)")

    def doIt(res: Id[Response[String]]): List[TableEntry] = {
      try {
        val planStr = res.unsafeBody
        val resXml = scala.xml.XML.loadString(planStr)

        //      println(s"""======> ${resXml.attributes.asAttrMap}""")

        val station = resXml.attribute("station").map(_.toString).getOrElse("-")

        val items = resXml \\ "s"

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

    val req = sttp.header("Accept", "application/xml").header("Authorization", "Bearer 8aa98ee641a28d95cddf612756cf1abd").get(uri"https://api.deutschebahn.com/timetables/v1/plan/$eva/$dateStr/$hourStr")

    req.send().map(doIt).unsafeRunSync()
  }

  private def timeTableEntries(eva: Int): List[TableEntry] = {
    val entries = getEntries(eva)
    val fchgs   = getFullChanges(eva)

    val fchgsMap = fchgs.map(t => t.id -> t).toMap

    val entries2 = entries map { te =>
      val delay = fchgsMap.get(te.id)

      delay.fold(te)(fc => te.copy(arDelay = fc.arDelay, dpDelay = fc.dpDelay))
    }

    entries2.sortWith(lessThan)
  }

  def timeTableServerEva(eva: Int) = Action {

    logger.info(s"timeTableServerEva($eva)")

    val station = getStationEva(eva)
    val entries = timeTableEntries(eva)

    Ok(views.html.index(station.fold("failed")(identity), entries.sortWith(lessThan)))
  }

  def timeTableServerJson(eva: Int) = Action {
    implicit val entriesWrites: Writes[TableEntry] = Json.writes[TableEntry]

    val entries = timeTableEntries(eva)

    Ok(Json.toJson(entries))
  }

  def timeTable = Action { _ =>
    Ok.sendFile(new java.io.File("public/bahnTimeTable.html"))
  }

  def betriebstellenJson(name: String) = Action {
    case class NameDs100(name: String, ds100: String)

    implicit val nameDs100Writes: Writes[NameDs100] =
      ((JsPath \ "name").write[String] and (JsPath \ "ds100").write[String]) (unlift(NameDs100.unapply))

    import java.nio.charset.{StandardCharsets => SC}
    import play.utils.UriEncoding

    val decodedName = UriEncoding.decodePath(name, SC.UTF_8)

    val stations = getBetriebsstellen(decodedName)

    val pairs = stations map (s => NameDs100(s.longName, s.ds100))

    Ok(Json.toJson(pairs))
  }

  implicit def ec: ExecutionContext = cc.executionContext

  private def findByDs100(ds100: String): Future[Option[Ds100Entry]] = {

    logger.info(s"findByDs100($ds100)")

    val f = mongo.find[DBDs100Entry](Json.obj("ds100" -> ds100)).first

    f.map { dbfM =>
      dbfM.map { dbf => Ds100Entry(dbf.ds100, dbf.eva, dbf.stationName, dbf.found) }
    }
  }

  private def findByNamePattern(name: String): Future[List[StationEntry]] = {

    logger.info(s"findByNamePattern($name)")

    val query = Json.obj("stationName" -> Json.obj("$regex" -> name, "$options" -> "i"), "found" -> true)

    val f = mongo.find[DBDs100Entry](query).list()

    f.map { dbfM =>
      dbfM.map { dbf => StationEntry(dbf.stationName, dbf.stationName, dbf.ds100, "", "") }
    }
  }

  private def storeDs100(ds100: String, name: String, eva: Int): Future[Void] = {

    logger.info(s"storeDs100($ds100, $name, $eva)")

    mongo.insertOne[DBDs100Entry](DBDs100Entry(_id = ds100, ds100 = ds100, eva = eva, stationName = name, found = true))

  }

  private def storeDs100Failed(ds100: String): Future[Void] = {

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

  def tokenAvailable = Action { Ok(Json.toJson(ioThrottlingBackend.avail.unsafeRunSync())) }
}
