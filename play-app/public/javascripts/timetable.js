
const timetable = new Vue({
	el: 'main',
	mounted: function() {
//	    this.$el.style.display = 'block'
    },
	data: {
		inputValue: '',
		inputDisabled: false,
		stations: [],
		protocol: location.protocol,
		hostname: location.hostname,
		port: location.port,
		counter: 0,
		counterVisible: false
	},
	methods: {
		handleInput: function() {
		},
		submitForm() {
		    var encoded = encodeURIComponent(this.response)
            var url     = this.protocol + '//' + this.hostname + ':' + this.port + "/betriebJson/" + this.inputValue
            this.counter = 0
            axios
              .get(url)
              .then(response => (this.stations = response.data))
		},
		chnData() {
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
		          station.name  = '<a href="' + url + '">' + station.name + '</a>'
		          station.ds100 = eva
                  this.counter = this.counter + 1
                  if (this.counter == this.stations.length) this.counterVisible = false
		        })
		        .catch(_ => {
    		      station.name  = '<strike>' + station.name + '</strike>'
    		      station.ds100 = '<strike>' + station.ds100 + '</strike>'
                  this.counter = this.counter + 1
                  if (this.counter == this.stations.length) this.counterVisible = false
		        })
		}
	}
})
