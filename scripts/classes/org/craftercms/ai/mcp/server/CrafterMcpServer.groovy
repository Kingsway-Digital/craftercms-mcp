package org.craftercms.ai.mcp.server;

@Grab('com.google.code.gson:gson:2.10.1')

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ServletException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CrafterMcpServer {

    private static final Logger logger = LoggerFactory.getLogger(CrafterMcpServer.class);
    private static final Gson gson = new Gson();
    private String serverId;
    private volatile boolean running;
    private ArrayList<McpTool> mcpTools = [];
    private LinkedBlockingQueue<JsonObject> streamQueue = new LinkedBlockingQueue<>();
    private Map<String, Set<String>> subscriptions = new ConcurrentHashMap<>();

    public ArrayList<McpTool> getMcpTools() { return mcpTools; }
    public void setMcpTools(ArrayList<McpTool> value) { mcpTools = value; }

    CrafterMcpServer() {
        this.serverId = UUID.randomUUID().toString();
        this.running = true;
        this.mcpTools = [];
    }

    // Regular HTTP POST
    void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!running) {
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            sendError(resp, null, -32000, "Server is shutting down");
            return;
        }

        // Log Authorization header
        String authHeader = req.getHeader("Authorization");
        if (authHeader != null) {
            logger.info("Received Authorization header: {}", authHeader);
        } else {
            logger.warn("No Authorization header received");
        }

        // Read request body
        StringBuilder jsonInput = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                jsonInput.append(line);
            }
        } catch (IOException e) {
            logger.error("Failed to read request body: {}", e.getMessage(), e);
            sendError(resp, null, -32600, "Invalid Request: Failed to read request body");
            return;
        }

        String jsonString = jsonInput.toString();
        logger.info("Received POST request: {}", jsonString);
        if (jsonString.trim().isEmpty()) {
            logger.warn("Empty request body received");
            sendError(resp, null, -32600, "Invalid Request: Empty request body");
            return;
        }

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Connection", "close");

        try (PrintWriter out = resp.getWriter()) {
            JsonObject response = handleRequest(jsonString, null);
            if (response == null) {
                logger.error("handleRequest returned null for input: {}", jsonString);
                sendError(resp, null, -32603, "Internal error: Null response from handler");
                return;
            }
            String responseString = gson.toJson(response);
            out.print(responseString);
            out.flush();
            logger.info("Sent response: {}", responseString);
        } catch (IOException e) {
            logger.error("IO error in doPost: {}", e.getMessage(), e);
            sendError(resp, null, -32000, "Server error: {}", e.getMessage());
        }
    }

    // Streaming HTTP POST
    void doPostStreaming(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!running) {
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            sendError(resp, null, -32000, "Server is shutting down");
            return;
        }

        // Log Authorization header
        String authHeader = req.getHeader("Authorization");
        if (authHeader != null) {
            logger.info("Received Authorization header: {}", authHeader);
        } else {
            logger.warn("No Authorization header received");
        }

        // Set headers for JSON response
        resp.setContentType("application/json");
        resp.setHeader("Connection", "close");
        resp.setCharacterEncoding("UTF-8");

        logger.info("Sending response headers: Content-Type=application/json, Connection=close");

        // Read request body
        StringBuilder jsonInput = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                jsonInput.append(line);
                logger.debug("Received streaming data: {}", line);
            }
        } catch (IOException e) {
            logger.error("Failed to read streaming request body: {}", e.getMessage(), e);
            sendError(resp, null, -32600, "Invalid Request: Failed to read request body");
            return;
        }

        String jsonString = jsonInput.toString();
        logger.info("Received streaming POST request: {}", jsonString);
        if (jsonString.trim().isEmpty()) {
            logger.warn("Empty streaming request body received");
            sendError(resp, null, -32600, "Invalid Request: Empty request body");
            return;
        }

        String subscriptionId = UUID.randomUUID().toString();
        Set<String> defaultEvents = new HashSet<>();
        defaultEvents.add("all");
        subscriptions.put(subscriptionId, defaultEvents);

        try (PrintWriter out = resp.getWriter()) {
            // Create JSON array for response
            JsonArray responseArray = new JsonArray();

            // Add connection/established notification
            JsonObject initNotification = new JsonObject();
            initNotification.addProperty("jsonrpc", "2.0");
            initNotification.addProperty("method", "connection/established");
            JsonObject initParams = new JsonObject();
            initParams.addProperty("subscriptionId", subscriptionId);
            initNotification.add("params", initParams);
            responseArray.add(initNotification);
            logger.info("Prepared connection/established for {}: {}", subscriptionId, gson.toJson(initNotification));

            // Handle initialize request
            JsonObject initializeResponse = handleRequest(jsonString, subscriptionId);
            if (initializeResponse == null) {
                logger.error("handleRequest returned null for initialize request: {}", jsonString);
                sendError(resp, null, -32603, "Internal error: Null response from handler");
                return;
            }
            responseArray.add(initializeResponse);
            logger.info("Prepared initialize response for {}: {}", subscriptionId, gson.toJson(initializeResponse));

            // Add roots/listChanged event
            JsonObject rootsNotification = new JsonObject();
            rootsNotification.addProperty("jsonrpc", "2.0");
            rootsNotification.addProperty("method", "roots/listChanged");
            JsonObject rootsParams = new JsonObject();
            JsonArray rootsList = new JsonArray();
            JsonObject root1 = new JsonObject();
            root1.addProperty("id", "root1");
            root1.addProperty("name", "Root One");
            root1.addProperty("path", "/api/craftermcp");
            root1.addProperty("type", "folder");
            rootsList.add(root1);
            rootsParams.add("roots", rootsList);
            rootsNotification.add("params", rootsParams);
            responseArray.add(rootsNotification);
            logger.info("Prepared roots/listChanged event for {}: {}", subscriptionId, gson.toJson(rootsNotification));

            // Send JSON array response
            String responseMessage = gson.toJson(responseArray);
            out.print(responseMessage);
            out.flush();
            logger.info("Sent JSON array response for {}: {}", subscriptionId, responseMessage);
        } catch (IOException e) {
            logger.error("Streaming error for {}: {}", subscriptionId, e.getMessage(), e);
        } finally {
            subscriptions.remove(subscriptionId);
            logger.info("Closed streaming connection for {}", subscriptionId);
        }
    }

    // Streaming HTTP GET (for subscriptions, if needed)
    void doGetStreaming(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!running) {
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            logger.warn("Streaming GET request rejected: Server is shutting down");
            return;
        }

        String authHeader = req.getHeader("Authorization");
        if (authHeader != null) {
            logger.info("Received Authorization header: {}", authHeader);
        } else {
            logger.warn("No Authorization header received");
        }

        resp.setContentType("application/json");
        resp.setHeader("Connection", "close");
        resp.setCharacterEncoding("UTF-8");

        logger.info("Sending response headers: Content-Type=application/json, Connection=close");

        String subscriptionId = UUID.randomUUID().toString();
        Set<String> defaultEvents = new HashSet<>();
        defaultEvents.add("all");
        subscriptions.put(subscriptionId, defaultEvents);

        try (PrintWriter out = resp.getWriter()) {
            JsonArray responseArray = new JsonArray();
            JsonObject rootsNotification = new JsonObject();
            rootsNotification.addProperty("jsonrpc", "2.0");
            rootsNotification.addProperty("method", "roots/listChanged");
            JsonObject rootsParams = new JsonObject();
            JsonArray rootsList = new JsonArray();
            JsonObject root1 = new JsonObject();
            root1.addProperty("id", "root1");
            root1.addProperty("name", "Root One");
            root1.addProperty("path", "/api/craftermcp");
            root1.addProperty("type", "folder");
            rootsList.add(root1);
            rootsParams.add("roots", rootsList);
            rootsNotification.add("params", rootsParams);
            responseArray.add(rootsNotification);
            String responseMessage = gson.toJson(responseArray);
            out.print(responseMessage);
            out.flush();
            logger.info("Sent roots/listChanged event for {}: {}", subscriptionId, responseMessage);
        } catch (IOException e) {
            logger.error("Streaming error for {}: {}", subscriptionId, e.getMessage(), e);
        } finally {
            subscriptions.remove(subscriptionId);
            logger.info("Closed streaming connection for {}", subscriptionId);
        }
    }

    private JsonObject handleRequest(String jsonInput, String subscriptionId) {
        try {
            if (jsonInput == null || jsonInput.trim().isEmpty()) {
                logger.warn("Empty or null JSON input");
                return createErrorResponse(null, -32600, "Invalid Request: Empty or null JSON input");
            }
            JsonObject request = gson.fromJson(jsonInput, JsonObject.class);
            if (request == null || !request.has("jsonrpc") || !request.get("jsonrpc").getAsString().equals("2.0")) {
                logger.warn("Invalid JSON-RPC request: {}", jsonInput);
                return createErrorResponse(null, -32600, "Invalid Request: Must be JSON-RPC 2.0");
            }
            if (!request.has("method")) {
                logger.warn("Missing method in JSON-RPC request: {}", jsonInput);
                return createErrorResponse(null, -32600, "Invalid Request: Missing method");
            }
            String method = request.get("method").getAsString();
            JsonElement id = request.get("id");
            JsonObject params = request.has("params") ? request.get("params").getAsJsonObject() : new JsonObject();

            logger.info("Processing JSON-RPC method: {}, id: {}", method, id);

            switch (method) {
                case "initialize":
                    return handleInitialize(id);
                case "tools/list":
                    return handleToolsList(id);
                case "tools/call":
                    return handleToolCall(id, params);
                case "events/check":
                    return handleEventsCheck(id);
                case "subscribe":
                    return handleSubscribe(id, params, subscriptionId);
                case "unsubscribe":
                    return handleUnsubscribe(id, params, subscriptionId);
                case "shutdown":
                    return handleShutdown(id);
                default:
                    return createErrorResponse(id, -32601, "Method not found: " + method);
            }
        } catch (JsonParseException e) {
            logger.error("JSON parse error: {}", e.getMessage(), e);
            return createErrorResponse(null, -32700, "Parse error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error processing request: {}", e.getMessage(), e);
            return createErrorResponse(null, -32603, "Internal error: " + e.getMessage());
        }
    }

    private JsonObject handleInitialize(JsonElement id) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);

        JsonObject result = new JsonObject();
        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", "CrafterMcpServer");
        serverInfo.addProperty("version", "1.0.0");
        serverInfo.addProperty("id", serverId);
        result.add("serverInfo", serverInfo);

        JsonObject capabilities = new JsonObject();
        capabilities.addProperty("tools", true);
        capabilities.addProperty("resources", true);
        capabilities.addProperty("prompts", true);
        capabilities.addProperty("streaming", true);
        capabilities.addProperty("subscriptions", true);
        result.add("capabilities", capabilities);
        result.addProperty("subscriptionUrl", "/api/craftermcp/stream.json");
        response.add("result", result);

        logger.info("Generated initialize response: {}", gson.toJson(response));
        return response;
    }

    private JsonObject handleToolsList(JsonElement id) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);

        JsonArray tools = new JsonArray();
        mcpTools.each { mcpToolRecord ->
            JsonObject currentTool = new JsonObject();
            currentTool.addProperty("name", mcpToolRecord.toolName);
            currentTool.addProperty("description", mcpToolRecord.toolDescription);

            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");

            JsonObject properties = new JsonObject();
            mcpToolRecord.params.each { param ->
                JsonObject property = new JsonObject();
                property.addProperty("type", param.type);
                property.addProperty("description", param.description);
                properties.add(param.name, property);
            }

            inputSchema.add("properties", properties);
            currentTool.add("inputSchema", inputSchema);
            tools.add(currentTool);
        }

        JsonObject result = new JsonObject();
        result.add("tools", tools);
        response.add("result", result);

        logger.info("Generated tools/list response: {}", gson.toJson(response));
        return response;
    }

    private JsonObject handleToolCall(JsonElement id, JsonObject params) {
        String toolName = params.get("name").getAsString();
        JsonObject arguments = params.has("arguments") ? params.get("arguments").getAsJsonObject() : new JsonObject();

        logger.info("Calling tool: {} with arguments: {}", toolName, gson.toJson(arguments));

        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);

        McpTool toolToCall = null;
        mcpTools.each { toolRecord ->
            if (toolRecord.toolName.equals(toolName)) {
                toolToCall = toolRecord;
            }
        }

        if (!toolToCall) {
            return createErrorResponse(id, -32602, "Invalid tool: " + toolName);
        } else {
            def callArgs = [];
            toolToCall.params.each { arg ->
                def argValue = ("" + params["arguments"][arg.name]).replaceAll("\"","");
                callArgs.add(argValue);
            }

            def toolResponse = toolToCall.call(callArgs);

            JsonArray content = new JsonArray();
            JsonObject textContent = new JsonObject();
            textContent.addProperty("type", "text");
            textContent.addProperty("text", toolResponse);
            content.add(textContent);

            JsonObject result = new JsonObject();
            result.add("content", content);
            response.add("result", result);
        }

        logger.info("Generated tool/call response: {}", gson.toJson(response));
        return response;
    }

    private JsonObject handleSubscribe(JsonElement id, JsonObject params, String subscriptionId) {
        if (subscriptionId == null) {
            return createErrorResponse(id, -32602, "Subscription ID required for streaming");
        }

        JsonArray events = params.has("events") ? params.get("events").getAsJsonArray() : new JsonArray();
        Set<String> eventSet = new HashSet<>();
        events.each { event -> eventSet.add(event.getAsString()) };

        if (eventSet.isEmpty()) {
            eventSet.add("all");
        }

        subscriptions.put(subscriptionId, eventSet);

        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        JsonObject result = new JsonObject();
        result.addProperty("subscriptionId", subscriptionId);
        response.add("result", result);

        logger.info("Subscription created: {} for events: {}", subscriptionId, eventSet);
        return response;
    }

    private JsonObject handleUnsubscribe(JsonElement id, JsonObject params, String subscriptionId) {
        if (subscriptionId == null) {
            return createErrorResponse(id, -32602, "Subscription ID required for streaming");
        }

        subscriptions.remove(subscriptionId);

        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        response.add("result", new JsonObject());

        logger.info("Subscription removed: {}", subscriptionId);
        return response;
    }

    private JsonObject handleEventsCheck(JsonElement id) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        JsonObject result = new JsonObject();
        result.addProperty("subscriptionUrl", "/api/craftermcp/stream.json");
        response.add("result", result);
        logger.info("Generated events/check response: {}", gson.toJson(response));
        return response;
    }

    private JsonObject handleShutdown(JsonElement id) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        response.add("result", new JsonObject());
        shutdown();
        logger.info("Generated shutdown response: {}", gson.toJson(response));
        return response;
    }

    private JsonObject createErrorResponse(JsonElement id, int code, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        if (id != null) {
            response.add("id", id);
        }
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        response.add("error", error);

        logger.warn("Generated error response: code={}, message={}", code, message);
        return response;
    }

    private void sendResponse(PrintWriter out, JsonObject response, boolean isStreaming) {
        String jsonResponse = gson.toJson(response);
        out.print(isStreaming ? jsonResponse + "\n" : jsonResponse);
        out.flush();
        logger.info("Sent response: {}", jsonResponse);
    }

    private void sendError(HttpServletResponse resp, JsonElement id, int code, String message) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            sendResponse(out, createErrorResponse(id, code, message), false);
        }
    }

    private void shutdown() {
        running = false;
        logger.info("Server shut down");
        JsonObject shutdownNotification = new JsonObject();
        shutdownNotification.addProperty("jsonrpc", "2.0");
        shutdownNotification.addProperty("method", "server/shutdown");
        JsonObject params = new JsonObject();
        params.addProperty("message", "Server is shutting down");
        shutdownNotification.add("params", params);
        streamQueue.offer(shutdownNotification);
        subscriptions.clear();
    }

    private boolean isSubscribed(String subscriptionId, JsonObject event) {
        String eventType = event.has("method") ? event.get("method").getAsString().split("/")[0] :
                          (event.has("event") ? event.get("event").getAsString() : "");
        Set<String> subscribedEvents = subscriptions.get(subscriptionId);
        return subscribedEvents != null && (subscribedEvents.contains("all") || subscribedEvents.contains(eventType));
    }
}