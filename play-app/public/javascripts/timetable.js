
const timetable = new Vue({
	el: 'main',
	mounted: function() {
//	    this.$el.style.display = 'block'
    },
	data: {
		inputValue: '',
		inputDisabled: false,
		response: '',
		stations: [],
		protocol: location.protocol,
		hostname: location.hostname,
		port: location.port
	},
	methods: {
		handleInput: function() {
		    this.response = this.inputValue
		},
		submitForm() {
		    var encoded = encodeURIComponent(this.response)
            var url     = this.protocol + '//' + this.hostname + ':' + this.port + "/betriebJson/" + this.response
//            alert('<' + url + '>')
            axios
              .get(url)
              .then(response => (this.stations = response.data))
		},
		chnData() {
            var arrayLength = this.stations.length
            for (var i = 0; i < arrayLength; i++) {
                this.stations[i].ds100 = '<' + this.stations[i].ds100 + '>'
            }
		},
		checkDs100(station) {
		    station.ds100 = '<' + this.stations[i].ds100 + '>'
		}
	}
})
