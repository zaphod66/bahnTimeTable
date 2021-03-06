
const timeTable = new Vue({
	el: 'main',
	mounted: function() {
        this.$el.style.display = 'block'
    },
	data: {
		inputValue: '',
		inputDisabled: false,
		stations: [],
		timeTable: '',
		currentStation: '',
		protocol: location.protocol,
		hostname: location.hostname,
		port: location.port,
		counter: 0,
		token: 0,
		tokenPoll: null,
		counterVisible: false,
		checkBtnVisible: false,
		tableVisible: false,
		buttonsVisible: false
	},
	created: function() {
	    this.tokenAvail();
	},
	beforeDestroy: function() {
	    clearInterval(this.tokenPoll);
	},
	filters: {
	    filterDate: function(date) {
	        if (date === undefined) { return ""; }
	        var splits = date.split("T");
	        return splits[1];
	    }
	},
	methods: {
		handleInput: function() {
		},
		tokenAvail: function() {
		    this.tokenPoll = setInterval( () => {
                var url = this.protocol + '//' + this.hostname + ':' + this.port + "/tokens"
                axios
                  .get(url)
                  .then(response => (this.token = response.data))
                  .catch(error => console.log('Error searchStations:' + error))
		    }, 500)
		},
		calcDiff: function(dateSch, dateAct) {
            if (dateSch === undefined || dateSch === "") { return ""; }
            if (dateAct === undefined || dateAct === "") { return ""; }
            var d1 = Date.parse(dateSch);
            var d2 = Date.parse(dateAct);
            var diff = (d2 - d1) / 60000; // in minutes

            var res = "";

            if (diff >= 0) {
                res = "+" + diff
            } else {
                res = "" + diff
            };

            return res;
		},
		searchStations: function() {
		    var encoded = encodeURIComponent(this.response)
            var url     = this.protocol + '//' + this.hostname + ':' + this.port + "/betriebJson/" + this.inputValue
            this.counter = 0
            this.checkBtnVisible = true
            this.tableVisible = false
            this.buttonsVisible = false
            this.stations = []

            axios
              .get(url)
              .then(response => (this.stations = response.data))
              .catch(error => console.log('Error searchStations:' + error))
		},
		checkData: function() {
            var arrayLength = this.stations.length
            this.stations.forEach(this.checkDs100)
            this.checkBtnVisible = false
		},
		checkDs100: function(station) {
		    var url = this.protocol + '//' + this.hostname + ':' + this.port + "/stationJson/" + station.ds100
            this.counterVisible = true
		    axios
		        .get(url)
		        .then(response => {
		          var eva = response.data.eva
		          var url = this.protocol + '//' + this.hostname + ':' + this.port + "/timeTable/" + eva
		          var lnk = '<a href="' + url + '" target="_blank">' + station.name + '</a>'
                  var btn = '<button class="btn submit" v-on:click="getTimeTable($st.ds100)">{{st.ds100}}</button>'
		          station.entry = lnk
		          station.eva   = eva
                  station.found = true
                  this.counter = this.counter + 1
                  if (this.counter >= this.stations.length) {
                    this.counterVisible = false
                    this.counter = 0
                    this.buttonsVisible = true
                    this.stations = this.stations.filter(s => s.found)
                  }
		        })
		        .catch(_ => {
    		      station.entry = '<strike>' + station.entry + '</strike>'
                  station.found = false
                  this.counter = this.counter + 1
                  if (this.counter >= this.stations.length) {
                      this.counterVisible = false
                      this.counter = 0
                      this.buttonsVisible = true
                      this.stations = this.stations.filter(s => s.found)
                  }
		        })
		},
		getTimeTable: function(name, eva) {

		    var url = this.protocol + '//' + this.hostname + ':' + this.port + "/timeTableJson/" + eva

		    axios
		        .get(url)
		        .then(response => {
		            this.timeTable = response.data
		            this.tableVisible = true
		            this.currentStation = name
		        })
		        .catch(_ => {
		            this.timeTable = ''
    		        this.tableVisible = false
		        })
		}
	}
})
