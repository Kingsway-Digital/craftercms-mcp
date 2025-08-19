def language = params.greetLang ? params.greetLang : ""
def result = [:]
result.greeting = "Unknown"

language = language.toLowerCase()

if("english".equals(language)) {
    result.greeting = "Holy jumpin' geez my son, my son"
}
else if("french".equals(language)) {
    result.greeting = "Bonjour"
}
else if("spanish".equals(language)) {
    result.greeting = "Hola"
}

return result