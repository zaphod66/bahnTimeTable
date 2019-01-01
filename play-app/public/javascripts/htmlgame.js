const game = new Vue({
	el: 'main',
//	mounted: function() {
//	    this.$el.style.display = 'block'
//	},
	data: {
		inputValue: '',
		html5: [],
		experimental: [],
		deprecated: [],
		state: 'off',
		time: 180,
		todo: els.html5.size,
		results: [],
		score: 0
	},
	computed: {
		done: function() {
			return this.html5.length
		},
		minutes: function() {
			return Math.floor(this.time / 60)
		},
		seconds: function() {
			const _secs = this.time % 60
			return _secs < 10 ? '0' + _secs : _secs
		},
		inputDisabled: function() {
			return this.state !== 'off' && this.state !== 'on'
		},
		pauseDisabled: function() {
			return this.state !== 'on' && this.state !== 'paused'
		}
	},
	watch: {
		state: function(newState) {
			if (newState === 'on')
				this.timer()
			else if (newState === 'over')
				this.getResults()
		}
	},
	methods: {
		handleInput: function() {
			if (this.state === 'off')
				this.state = 'on'

			groups.some(group => {
				if (els[group].has(this.inputValue)) {
					if (this[group].indexOf(this.inputValue) < 0) {
						this[group] = this[group].concat(this.inputValue).sort();
						this.inputValue = '';
					}

					return true;
				}
			})

			if (this.done === this.todo)
				this.state = 'over'
		},

		timer: function() {
			const interval  = 500,
			      startTime = this.time,
			      start     = Date.now()
			let   expected  = 0
			const step      = () => {
			    if (this.state === 'over') return
                if (this.state === 'paused') return
      
                expected += interval
      
                let diff       = Date.now() - start - expected
                let passedSecs = Math.round(expected / 1000)
      
                this.time = Math.max(0, startTime - passedSecs)
      
                if (this.time === 0) return this.state = 'over'
      
                setTimeout(step, Math.max(0, interval - diff))
			}
      
			setTimeout(step, interval)
		}, 

		pause: function() {
			this.state = (this.state === 'paused') ? 'on' : 'paused'
		},

		restart: function() {
			this.inputValue   = ''
			this.state        = 'off'
			this.time         = 180
			this.html5        = []
			this.deprecated   = []
			this.experimental = []
			this.results      = []
		},

        getScore: function() {
            let _score = this.html5.length
            _score += Math.floor((this.experimental.length - this.deprecated.length) / 2)
            _score += Math.round((this.time * this.html5.length) / (3 * this.todo))

            if (this.html5.length === this.todo)
                _score += 100

            this.score = _score
        },

		getResults: function() {
            this.getScore()

            gamedata.forEach( group => {
                const found = [],
                      missing = []

                if (group.value) return

                group.elements.forEach( elem => {
                    if (this.html5.indexOf(elem.name) >= 0)
                        found.push(elem)
                    else
                        missing.push(elem)
                })

                this.results.push({
                    name: group.name,
                    total: group.elements.length,
                    found: found,
                    missing: missing
                })
            })
		}
	}
})

