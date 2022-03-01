#!/bin/bash
mongoexport -h localhost -d btt -c ds100 --type=csv --noHeaderLine --fields ds100 -q '{ "$and": [ { "ds100": { "$regex": "^X.*$" } }, { "found": true } ] }' --out ds100-Xfalse.txt
cat ds100-Xfalse.txt | sed 's: :%20:g' > ds100-Xfalse-escaped.txt
cat ds100-Xfalse-escaped.txt | awk '{ print "curl localhost:9000/stationJson/"$0; }' > ds100-Xfalse-curl.txt
