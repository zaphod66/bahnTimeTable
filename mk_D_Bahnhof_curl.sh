cat D_Bahnhof_2017_09.csv | cut -d ';' -f 2 | sed -e '/^\s*$/d' | sed 's: :%20:g' | tr ',' '\n' | awk '{ print "curl localhost:9000/stationJson/"$0; }'
