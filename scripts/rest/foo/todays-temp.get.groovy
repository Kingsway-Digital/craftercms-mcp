def city = params.city ? params.city : "New York"

def weatherService = new foo.WeatherService()

return weatherService.getTodaysHighTemp(city)