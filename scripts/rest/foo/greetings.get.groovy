def language = params.greetLang ? params.greetLang : ""
def resp = "Unknown"

language = language.toLowerCase()

if("english".equals(language)) {
    resp = "Holy jumpin' geez my son, my son"
}
else if("french".equals(language)) {
    resp = "Bonjour"
}
else if("spanish".equals(language)) {
    resp = "Hola"
}

return resp