def city = params.city ? params.city : ""
def temp = "Unknown"

city = city.toLowerCase()

if("new york".equals(city)) {
    temp = "90 degrees"
}
else if("chicago".equals(city)) {
    temp = "45 degrees"
}

return temp