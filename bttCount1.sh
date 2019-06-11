mongo <<EOF
use btt
db.ds100.count()
db.ds100.count( { found: true } )
db.ds100.count( { found: false } )
EOF
