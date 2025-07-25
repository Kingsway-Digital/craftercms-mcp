@Grab('com.google.code.gson:gson:2.10.1')

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import groovy.json.JsonSlurper

def gson = new Gson()
def request = gson.fromJson(request.reader.text, JsonObject.class)
def id = request.get("id")
def server = applicationContext["crafterMcpServer"]

def result = server.handleToolsList(id)   

return gson.fromJson(result, Map.class)