package org.craftercms.ai

@Grab('com.google.code.gson:gson:2.10.1')
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.ServletException
import java.io.BufferedReader
import java.io.IOException
import java.io.PrintWriter
import java.time.Instant
import java.util.UUID
import java.util.logging.Logger
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

// MCP server with HTTP Servlets and synchronous event checking
class CrafterMcpServer extends HttpServlet {
    private static final Gson gson = new Gson()
    private static final Logger logger = Logger.getLogger(CrafterMcpServer.class.getName())
    private String serverId
    private boolean running
    


    CrafterMcpServer() {
        this.serverId = UUID.randomUUID().toString()
        this.running = true
    }

    @Override
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

    @Override
    void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!running) {
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE)
            logger.warning("GET request rejected: Server is shutting down")
            return
        }

        // All GET requests return 404 (no SSE support)
        resp.setStatus(HttpServletResponse.SC_NOT_FOUND)
        logger.warning("Invalid GET request path: ${req.getServletPath()}")
    }

    // Handle incoming JSON-RPC requests
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
                case "resources/read":
                    handleResourceRead(id, params, out)
                    break
                case "prompts/list":
                    handlePromptsList(id, out)
                    break
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
            logger.severe("Parse error: ${e.getMessage()}")
            sendError(out, null, -32700, "Parse error: ${e.getMessage()}")
        }
    }

    // Handle initialization handshake
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

    // Handle tools/list request
    def handleToolsList(JsonElement id, PrintWriter out) {
    }

    def handleToolsList(JsonElement id) {
        JsonObject response = new JsonObject()
        response.addProperty("jsonrpc", "2.0")
        response.add("id", id)

        JsonArray tools = new JsonArray()

        // getCurrentTime tool
        JsonObject timeTool = new JsonObject()
        timeTool.addProperty("name", "getCurrentTime")
        timeTool.addProperty("description", "Returns the current server time")
        JsonObject timeInputSchema = new JsonObject()
        timeInputSchema.addProperty("type", "object")
        timeInputSchema.add("properties", new JsonObject())
        timeTool.add("inputSchema", timeInputSchema)
        tools.add(timeTool)

        // checkIngredientAvailability tool
        JsonObject ingredientTool = new JsonObject()
        ingredientTool.addProperty("name", "checkIngredientAvailability")
        ingredientTool.addProperty("description", "Check if a specific ingredient is available in the inventory")
        
        JsonObject inputSchema = new JsonObject()
        inputSchema.addProperty("type", "object")
        
        JsonObject properties = new JsonObject()
        JsonObject nameProperty = new JsonObject()
        nameProperty.addProperty("type", "string")
        nameProperty.addProperty("description", "The name of the ingredient to check")
        properties.add("name", nameProperty)
        
        inputSchema.add("properties", properties)
        
        JsonArray required = new JsonArray()
        required.add("name")
        inputSchema.add("required", required)
        
        ingredientTool.add("inputSchema", inputSchema)
        tools.add(ingredientTool)

        JsonObject result = new JsonObject()
        result.add("tools", tools)
        response.add("result", result)

        return response
    }















    // Handle tool calls
    private void handleToolCall(JsonElement id, JsonObject params, PrintWriter out) {
        String toolName = params.get("name").getAsString()
        JsonObject arguments = params.has("arguments") ? params.get("arguments").getAsJsonObject() : new JsonObject()
        
        logger.info("Calling tool: $toolName with arguments: ${gson.toJson(arguments)}")
        
        JsonObject response = new JsonObject()
        response.addProperty("jsonrpc", "2.0")
        response.add("id", id)

        switch (toolName) {
            case "checkIngredientAvailability":
                handleCheckIngredientAvailability(response, arguments)
                break
            default:
                sendError(out, id, -32602, "Invalid tool: $toolName")
                return
        }

        out.println(gson.toJson(response))
        logger.info("Sent tool call response: ${gson.toJson(response)}")
    }

    // Sample ingredient inventory - you can replace this with database calls or external API calls
    private static final Set<String> availableIngredients = [
        "flour", "sugar", "butter", "milk", "vanilla", "chocolate", "salt", 
        "baking powder", "olive oil", "garlic", "onion", "tomatoes", "cheese", 
        "chicken", "beef", "rice", "basil", "oregano"
    ].toSet()

    private void handleCheckIngredientAvailability(JsonObject response, JsonObject arguments) {
        String ingredientName = arguments.has("name") ? arguments.get("name").getAsString().toLowerCase().trim() : ""
        
        if (ingredientName.isEmpty()) {
            JsonArray content = new JsonArray()
            JsonObject textContent = new JsonObject()
            textContent.addProperty("type", "text")
            textContent.addProperty("text", "Error: ingredient name is required")
            content.add(textContent)

            JsonObject result = new JsonObject()
            result.add("content", content)
            response.add("result", result)
            return
        }

        boolean isAvailable = availableIngredients.contains(ingredientName)
        
        JsonArray content = new JsonArray()
        JsonObject textContent = new JsonObject()
        textContent.addProperty("type", "text")
        textContent.addProperty("text", "Ingredient '${ingredientName}' is ${isAvailable ? 'available' : 'not available'} in inventory.")
        content.add(textContent)

        JsonObject result = new JsonObject()
        result.add("content", content)
        result.addProperty("isAvailable", isAvailable)
        response.add("result", result)
        
        logger.info("Ingredient check: '${ingredientName}' -> ${isAvailable}")
    }

    // Handle resource read requests
    private void handleResourceRead(JsonElement id, JsonObject params, PrintWriter out) {
        String uri = params.get("uri").getAsString()
        JsonObject response = new JsonObject()
        response.addProperty("jsonrpc", "2.0")
        response.add("id", id)

        if ("file://example.txt" == uri) {
            JsonArray content = new JsonArray()
            JsonObject textContent = new JsonObject()
            textContent.addProperty("type", "text")
            textContent.addProperty("text", "This is a sample text file content.")
            content.add(textContent)

            JsonObject result = new JsonObject()
            result.add("content", content)
            response.add("result", result)
        } else {
            sendError(out, id, -32602, "Invalid resource URI: $uri")
            return
        }

        out.println(gson.toJson(response))
    }

    // Handle prompts list request
    private void handlePromptsList(JsonElement id, PrintWriter out) {
        JsonObject response = new JsonObject()
        response.addProperty("jsonrpc", "2.0")
        response.add("id", id)
 
        JsonArray prompts = new JsonArray()
        JsonObject prompt = new JsonObject()
        prompt.addProperty("name", "welcome")
        prompt.addProperty("description", "Welcome message prompt")
        prompts.add(prompt)

        JsonObject result = new JsonObject()
        result.add("prompts", prompts)
        response.add("result", result)

        out.println(gson.toJson(response))
    }

    // Handle events/check request
    private void handleEventsCheck(JsonElement id, PrintWriter out) {
        JsonObject response = new JsonObject()
        response.addProperty("jsonrpc", "2.0")
        response.add("id", id)

        // Simulate a listChanged notification as a synchronous response
        JsonObject result = new JsonObject()
        JsonArray notifications = new JsonArray()
        JsonObject notification = new JsonObject()
        notification.addProperty("method", "listChanged")
        JsonObject params = new JsonObject()
        JsonArray uris = new JsonArray()
        uris.add("file://example.txt")
        params.add("uris", uris)
        notification.add("params", params)
        notifications.add(notification)
        result.add("notifications", notifications)

        response.add("result", result)
        out.println(gson.toJson(response))
        logger.info("Sent events/check response: ${gson.toJson(response)}")
    }

    // Handle shutdown request
    private void handleShutdown(JsonElement id, PrintWriter out) {
        JsonObject response = new JsonObject()
        response.addProperty("jsonrpc", "2.0")
        response.add("id", id)
        response.add("result", new JsonObject())
        out.println(gson.toJson(response))
        shutdown()
    }

    // Send JSON-RPC error response
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
        logger.warning("Sent error response: code=$code, message=$message")
    }

    // Send JSON-RPC error response with HTTP status
    private void sendError(HttpServletResponse resp, JsonElement id, int code, String message) throws IOException {
        resp.setContentType("application/json")
        resp.setCharacterEncoding("UTF-8")
        try (PrintWriter out = resp.getWriter()) {
            sendError(out, id, code, message)
        }
    }

    // Shutdown the server
    private void shutdown() {
        running = false
        logger.info("Server shut down")
    }
}