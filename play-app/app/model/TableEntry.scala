package model

case class TableEntry(id: String, line: String, track: String,
                      arrival: String, departure: String,
                      depart: String, dest: String,
                      arDelay: String = "", dpDelay: String = "")
