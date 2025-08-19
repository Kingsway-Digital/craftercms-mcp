def city = params.city ? params.city : ""
def result = [:]
result.temp = "Unknown"

city = city.toLowerCase()

if("new york".equals(city)) {
    result.temp = "90 degrees"
}
else if("chicago".equals(city)) {
    result.temp = "45 degrees"
}

return result
