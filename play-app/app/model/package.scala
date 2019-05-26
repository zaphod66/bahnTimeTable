import play.api.libs.json.{Format, Json}

package object model {
  implicit val dbDs100Format: Format[DBDs100Entry] = Json.format[DBDs100Entry]
}
