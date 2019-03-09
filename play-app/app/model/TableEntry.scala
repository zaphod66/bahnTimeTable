package model

import java.time.LocalDateTime

case class TableEntry(id: String, line: String, track: String,
                      arrival: Option[LocalDateTime], departure: Option[LocalDateTime],
                      depart: String, dest: String,
                      arDelay: Option[LocalDateTime] = None, dpDelay: Option[LocalDateTime] = None)
