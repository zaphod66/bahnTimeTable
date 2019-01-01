const timetable = new Vue({
	el: 'main',
//	mounted: function() {
//	    this.$el.style.display = 'block'
//	},
	data: {
		inputValue: '',
		inputDisabled: false,
		response: ''
	},
	methods: {
		handleInput: function() {
//		    console.log(this.inputValue)

		    this.response = this.inputValue
		}
	}
})
