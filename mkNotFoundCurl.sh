#!/bin/bash
mongoexport -h localhost -d btt -c ds100 --type=csv --fields ds100 -q '{ "found": false }' --out ds100-false.txt
cat ds100-false.txt | sed 's: :%20:g' > ds100-false-escaped.txt
cat ds100-false-escaped.txt | awk '{ print "curl localhost:9000/stationJson/"$0; }' > ds100-false-curl.txt
