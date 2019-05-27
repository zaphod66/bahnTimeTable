package model

import cn.playscala.mongo.annotations.Entity

@Entity("ds100")
case class DBDs100Entry(_id: String, ds100: String, eva: Int, stationName: String, found: Boolean)
