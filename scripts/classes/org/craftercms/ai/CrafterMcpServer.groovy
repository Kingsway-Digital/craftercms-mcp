package org.craftercms.ai

@Grab('com.google.code.gson:gson:2.10.1')
@Grab(group='org.springframework.ai', module='spring-ai-client-chat', version='1.0.0', initClass=false, systemClassLoader=true)

import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.ServletException
import java.io.BufferedReader
import java.io.IOException
import java.io.PrintWriter
import java.time.Instant

import java.util.UUID
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.function.Function
import org.springframework.ai.tool.function.FunctionToolCallback

class CrafterMcpServer {

    private static final Logger logger = LoggerFactory.getLogger(CrafterMcpServer.class)
    private static final Gson gson = new Gson()
    private String serverId
    private boolean running
    private ArrayList<McpTool> mcpTools = []    

    public ArrayList<FunctionToolCallback> getMcpTools() { return mcpTools }
    public void setMcpTools(ArrayList<McpTool> value) { mcpTools = value }

    CrafterMcpServer() {
        this.serverId = UUID.randomUUID().toString()
        this.running = true
        this.mcpTools = []
    }  

    void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!running) {
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE)
            sendError(resp, null, -32000, "Server is shutting down")
            return
        }

        StringBuilder jsonInput = new StringBuilder()
        try (BufferedReader reader = req.getReader()) {
            String line
            while ((line = reader.readLine()) != null) {
                jsonInput.append(line)
            }
        }

        resp.setContentType("application/json")
        resp.setCharacterEncoding("UTF-8")

        try (PrintWriter out = resp.getWriter()) {
            logger.info("Received POST request: $jsonInput")
            handleRequest(jsonInput.toString(), out)
        } catch (Exception e) {
            logger.severe("Server error: ${e.getMessage()}")
            sendError(resp, null, -32000, "Server error: ${e.getMessage()}")
        }
    }

    void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!running) {
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE)
            logger.warn("GET request rejected: Server is shutting down")
            return
        }

        resp.setStatus(HttpServletResponse.SC_NOT_FOUND)
        logger.warn("Invalid GET request path: ${req.getServletPath()}")
    }

    private void handleRequest(String jsonInput, PrintWriter out) {

        try {
            JsonObject request = gson.fromJson(jsonInput, JsonObject.class)
            String method = request.get("method").getAsString()
            JsonElement id = request.get("id")
            JsonObject params = request.has("params") ? request.get("params").getAsJsonObject() : new JsonObject()

            logger.info("Processing JSON-RPC method: $method, id: $id")

            switch (method) {
                case "initialize":
                    handleInitialize(id, out)
                    break
                case "tools/list":
                    handleToolsList(id, out)
                    break
                case "tools/call":
                    handleToolCall(id, params, out)
                    break
                // case "resources/read":
                //     handleResourceRead(id, params, out)
                //     break
                // case "prompts/list":
                //     handlePromptsList(id, out)
                //     break
                case "events/check":
                    handleEventsCheck(id, out)
                    break
                case "shutdown":
                    handleShutdown(id, out)
                    break
                default:
                    sendError(out, id, -32601, "Method not found: $method")
            }
        } catch (Exception e) {
            logger.error("Parse error: ${e.getMessage()}")
            sendError(out, null, -32700, "Parse error: ${e.getMessage()}")
        }
    }

    private void handleInitialize(JsonElement id, PrintWriter out) {
        JsonObject response = new JsonObject()
        response.addProperty("jsonrpc", "2.0")
        response.add("id", id)

        JsonObject result = new JsonObject()
        JsonObject serverInfo = new JsonObject()
        serverInfo.addProperty("name", "CrafterMcpServer")
        serverInfo.addProperty("version", "1.0.0")
        serverInfo.addProperty("id", serverId)
        result.add("serverInfo", serverInfo)

        JsonObject capabilities = new JsonObject()
        capabilities.addProperty("tools", true)
        capabilities.addProperty("resources", true)
        capabilities.addProperty("prompts", true)

        result.add("capabilities", capabilities)
        response.add("result", result)

        out.println(gson.toJson(response))
        logger.info("Sent initialize response: ${gson.toJson(response)}")
    }

    def handleToolsList(JsonElement id, PrintWriter out) {
        JsonObject response = new JsonObject()
        response.addProperty("jsonrpc", "2.0")
        response.add("id", id)

        JsonArray tools = new JsonArray()

        mcpTools.each { mcpToolRecord -> 

            // tool method            
            JsonObject currentTool = new JsonObject()
            currentTool.addProperty("name", mcpToolRecord.toolName)
            currentTool.addProperty("description", mcpToolRecord.toolDescription)

            // input
            JsonObject inputSchema = new JsonObject()
            inputSchema.addProperty("type", "object")
 
                // parameters
                JsonObject properties = new JsonObject()
                mcpToolRecord.params.each { param ->
                    JsonObject property = new JsonObject()
                    property.addProperty("type", param.type)
                    property.addProperty("description", param.description)
                    properties.add(param.name, property)
                }
       
            inputSchema.add("properties", properties)
        
            //JsonArray required = new JsonArray()
            //required.add("name")
            //inputSchema.add("required", required)
            
            currentTool.add("inputSchema", inputSchema)
            tools.add(currentTool)
        }

        JsonObject result = new JsonObject()
        result.add("tools", tools)
        response.add("result", result)

        out.println(gson.toJson(response))
    }

    private void handleToolCall(JsonElement id, JsonObject params, PrintWriter out) {
        String toolName = params.get("name").getAsString()
        JsonObject arguments = params.has("arguments") ? params.get("arguments").getAsJsonObject() : new JsonObject()
        
        logger.info("Calling tool: $toolName with arguments: ${gson.toJson(arguments)}")
        
        JsonObject response = new JsonObject()
        response.addProperty("jsonrpc", "2.0")
        response.add("id", id)

        McpTool toolToCall = null;
        mcpTools.each { toolRecord ->
            if(toolRecord.toolName.equals(toolName)) {
                toolToCall = toolRecord
            }
        }

        if(!toolToCall) {
            sendError(out, id, -32602, "Invalid tool: $toolName")
            return
        }
        else {
            def argValue = (""+params["arguments"]["ingrdient"]).replaceAll("\"","")
            def toolResponse = toolToCall.call([argValue])

            JsonArray content = new JsonArray()
            JsonObject textContent = new JsonObject()
            textContent.addProperty("type", "text")
            textContent.addProperty("text", toolResponse)
            content.add(textContent)

            JsonObject result = new JsonObject()
            result.add("content", content)
            //result.addProperty("isAvailable", isAvailable)
            response.add("result", result)
        }

        out.println(gson.toJson(response))
        logger.info("Sent tool call response: ${gson.toJson(response)}")
    }

    private void handleShutdown(JsonElement id, PrintWriter out) {
        JsonObject response = new JsonObject()
        response.addProperty("jsonrpc", "2.0")
        response.add("id", id)
        response.add("result", new JsonObject())
        out.println(gson.toJson(response))
        shutdown()
    }

    private void sendError(PrintWriter out, JsonElement id, int code, String message) {
        JsonObject response = new JsonObject()
        response.addProperty("jsonrpc", "2.0")
        if (id != null) {
            response.add("id", id)
        }
        JsonObject error = new JsonObject()
        error.addProperty("code", code)
        error.addProperty("message", message)
        response.add("error", error)
        out.println(gson.toJson(response))
        logger.warn("Sent error response: code=$code, message=$message")
    }

    private void sendError(HttpServletResponse resp, JsonElement id, int code, String message) throws IOException {
        resp.setContentType("application/json")
        resp.setCharacterEncoding("UTF-8")
        try (PrintWriter out = resp.getWriter()) {
            sendError(out, id, code, message)
        }
    }

    private void shutdown() {
        running = false
        logger.info("Server shut down")
    }
}




// package foo

// import java.util.function.Function

// public class IsIngredientAvailableFunction implements Function<String, String> {
    
//     public RecipeService recipeService
//     public RecipeService getRecipeService() { return recipeService }
//     public void setRecipeService(RecipeService value) { recipeService = value }


//     public IsIngredientAvailableFunction() { }

//     public String apply(String value) {
//         return recipeService.isIngredientAvailable(value)
//     }
// }

    // def checkAvailabilityFuncCallWrapper = FunctionCallbackWrapper.builder(new CheckAvailabilityTool())
    // .withName("CheckAvailability")
    // .withDescription("Returns true if rooms are available")
    // .withResponseConverter((response) -> "" + response.available())
    // .build()


    // public class CheckAvailabilityTool implements Function<CheckAvailabilityTool.Request, CheckAvailabilityTool.Response> {

    //     public CheckAvailabilityTool() {}
    //     public CheckAvailabilityTool(boolean available) {}
        
    //     public record Request(String date) {}
    //     public record Response(CheckAvailabilityTool tool, java.lang.Boolean available) {}

    //     @Override
    //     public Response apply(Request request) {
            
    //         return new Response(true)
    //     }
    // }

//}