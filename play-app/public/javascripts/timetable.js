
const timetable = new Vue({
	el: 'main',
	mounted: function() {
//	    this.$el.style.display = 'block'
    },
	data: {
		inputValue: '',
		inputDisabled: false,
		stations: [],
		timeTable: '',
		protocol: location.protocol,
		hostname: location.hostname,
		port: location.port,
		counter: 0,
		counterVisible: false,
		tableVisible: false
	},
	methods: {
		handleInput: function() {
		},
		submitForm() {
		    var encoded = encodeURIComponent(this.response)
            var url     = this.protocol + '//' + this.hostname + ':' + this.port + "/betriebJson/" + this.inputValue
            this.counter = 0
            this.tableVisible = false

            axios
              .get(url)
              .then(response => (this.stations = response.data))
		},
		checkData() {
            var arrayLength = this.stations.length
            this.stations.forEach(this.checkDs100)
		},
		checkDs100(station) {
		    var url = this.protocol + '//' + this.hostname + ':' + this.port + "/stationJson/" + station.ds100
            this.counterVisible = true
		    axios
		        .get(url)
		        .then(response => {
		          var eva = response.data.eva
		          var url = this.protocol + '//' + this.hostname + ':' + this.port + "/timeTable/" + eva
		          var lnk = '<a href="' + url + '" target="_blank">' + station.name + '</a>'
//		          var btn = '<button class="btn submit" v-on:click="getTimeTable(' + eva + ')">' + station.name + '</button>'
//                  var btn = '<button class="btn submit" v-on:click="getTimeTable($st.ds100)">{{st.ds100}}</button>'
                  var btn = '<button class="btn submit" v-on:click="getTimeTable($st.ds100)">{{st.ds100}}</button>'
//		          station.name  = lnk
		          station.link  = lnk
		          station.ds100 = eva
		          station.eva   = eva
                  this.counter = this.counter + 1
                  if (this.counter == this.stations.length) { this.counterVisible = false; this.counter = 0 }
		        })
		        .catch(_ => {
    		      station.name  = '<strike>' + station.name + '</strike>'
    		      station.ds100 = '<strike>' + station.ds100 + '</strike>'
                  this.counter = this.counter + 1
                  if (this.counter == this.stations.length) { this.counterVisible = false; this.counter = 0 }
		        })
		},
		getTimeTable: function(eva) {
		    var url = this.protocol + '//' + this.hostname + ':' + this.port + "/timeTableJson/" + eva

		    axios
		        .get(url)
		        .then(response => {
		            this.timeTable = response.data
		            this.tableVisible = true
		        })
		        .catch(_ => {
		            this.timeTable = ''
    		        this.tableVisible = false
		        })
		}
	}
})
