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
    private Map<String, String> sessions = new ConcurrentHashMap<>(); // Track sessions

    public ArrayList<McpTool> getMcpTools() { return mcpTools; }
    public void setMcpTools(ArrayList<McpTool> value) { mcpTools = value; }

    CrafterMcpServer() {
        this.serverId = UUID.randomUUID().toString();
        this.running = true;
        this.mcpTools = [];
    }

    // Handle CORS preflight requests
    void doOptionsStreaming(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, Mcp-Session-Id");
        resp.setHeader("Access-Control-Expose-Headers", "Mcp-Session-Id");
        resp.setStatus(HttpServletResponse.SC_OK);
        logger.info("Handled OPTIONS preflight request");
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

        // Check Accept header to determine response type
        String acceptHeader = req.getHeader("Accept");
        logger.info("Received Accept header: {}", acceptHeader);

        // Check for existing session
        String existingSessionId = req.getHeader("Mcp-Session-Id");
        logger.info("Received Mcp-Session-Id header: {}", existingSessionId);

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
        logger.info("Received streaming POST request: {}", jsonString);
        if (jsonString.trim().isEmpty()) {
            logger.warn("Empty request body received");
            sendError(resp, null, -32600, "Invalid Request: Empty request body");
            return;
        }

        // Parse the request to check if it's an initialize request
        boolean isInitializeRequest = false;
        String sessionId = existingSessionId;
        
        try {
            JsonObject request = gson.fromJson(jsonString, JsonObject.class);
            String method = request.has("method") ? request.get("method").getAsString() : "";
            isInitializeRequest = "initialize".equals(method);
            
            // For initialize requests, create a new session
            if (isInitializeRequest) {
                sessionId = UUID.randomUUID().toString();
                sessions.put(sessionId, serverId);
                logger.info("Created new session: {} for initialize request", sessionId);
            } else if (existingSessionId == null || !sessions.containsKey(existingSessionId)) {
                // Non-initialize request without valid session
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                sendError(resp, null, -32002, "Invalid session: Missing or invalid Mcp-Session-Id header");
                return;
            }
        } catch (Exception e) {
            logger.warn("Failed to parse request: {}", e.getMessage());
            sendError(resp, null, -32700, "Parse error: " + e.getMessage());
            return;
        }

        // According to MCP spec, server MUST check Accept header and respond appropriately
        // If Accept includes text/event-stream AND application/json, server can choose
        // If Accept only includes application/json, server MUST respond with JSON
        boolean clientWantsSSE = acceptHeader != null && 
                                acceptHeader.contains("text/event-stream") &&
                                acceptHeader.contains("application/json");
        boolean clientOnlyWantsJSON = acceptHeader != null && 
                                     acceptHeader.contains("application/json") &&
                                     !acceptHeader.contains("text/event-stream");

        // Set response headers
        resp.setCharacterEncoding("UTF-8");
        
        // Add CORS headers for browser compatibility
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, Mcp-Session-Id");
        resp.setHeader("Access-Control-Expose-Headers", "Mcp-Session-Id");
        
        if (sessionId != null) {
            resp.setHeader("Mcp-Session-Id", sessionId);
            logger.info("Set Mcp-Session-Id header: {}", sessionId);
        }
        
        resp.setContentType("application/json");
        resp.setHeader("Connection", "close");

        try (PrintWriter out = resp.getWriter()) {
            JsonObject response = handleRequest(jsonString, sessionId);
            if (response == null) {
                logger.error("handleRequest returned null for input: {}", jsonString);
                sendError(resp, null, -32603, "Internal error: Null response from handler");
                return;
            }
            String responseString = gson.toJson(response);
            out.print(responseString);
            out.flush();
            logger.info("Sent streaming response with session {}: {}", sessionId, responseString);
        } catch (IOException e) {
            logger.error("IO error in streaming doPost: {}", e.getMessage(), e);
            sendError(resp, null, -32000, "Server error: " + e.getMessage());
        }
    }

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

        // Check for session ID in GET request
        String existingSessionId = req.getHeader("Mcp-Session-Id");
        if (existingSessionId == null || !sessions.containsKey(existingSessionId)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            logger.warn("GET streaming request without valid session");
            return;
        }

        // For streamable HTTP, GET can be used for long-polling or notifications
        // But we'll keep it simple and just return current state
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Connection", "close");
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Mcp-Session-Id", existingSessionId);

        logger.info("Handling streamable HTTP GET for session: {}", existingSessionId);

        try (PrintWriter out = resp.getWriter()) {
            // Return empty result for now - in a full implementation, 
            // this could return pending notifications
            JsonObject response = new JsonObject();
            response.addProperty("jsonrpc", "2.0");
            response.addProperty("id", null);
            JsonArray result = new JsonArray();
            response.add("result", result);
            
            String responseString = gson.toJson(response);
            out.print(responseString);
            out.flush();
            logger.info("Sent GET response for session {}: {}", existingSessionId, responseString);
        } catch (IOException e) {
            logger.error("IO error in streamable GET: {}", e.getMessage(), e);
        }
    }

    private JsonObject handleRequest(String jsonInput, String sessionId) {
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

            logger.info("Processing JSON-RPC method: {}, id: {}, session: {}", method, id, sessionId);

            switch (method) {
                case "initialize":
                    return handleInitialize(id, sessionId);
                case "tools/list":
                    return handleToolsList(id);
                case "tools/call":
                    return handleToolCall(id, params);
                case "roots/list":
                    return handleRootsList(id);
                case "resources/list":
                    return handleResourcesList(id);
                case "prompts/list":
                    return handlePromptsList(id);
                case "notifications/list":
                    return handleNotificationsList(id);
                case "subscribe":
                    return handleSubscribe(id, params, sessionId);
                case "unsubscribe":
                    return handleUnsubscribe(id, params, sessionId);
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

    private JsonObject handleInitialize(JsonElement id, String sessionId) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);

        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", "2025-06-18");
        
        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", "CrafterMcpServer");
        serverInfo.addProperty("version", "1.0.0");
        result.add("serverInfo", serverInfo);

        JsonObject capabilities = new JsonObject();
        
        // Tools capability
        JsonObject tools = new JsonObject();
        tools.addProperty("listChanged", false);
        capabilities.add("tools", tools);
        
        // Resources capability  
        JsonObject resources = new JsonObject();
        resources.addProperty("subscribe", false);
        resources.addProperty("listChanged", false);
        capabilities.add("resources", resources);
        
        // Prompts capability
        JsonObject prompts = new JsonObject();
        prompts.addProperty("listChanged", false);
        capabilities.add("prompts", prompts);
        
        // Roots capability - this is what the client specifically requested
        JsonObject roots = new JsonObject();
        roots.addProperty("listChanged", true);
        capabilities.add("roots", roots);
        
        result.add("capabilities", capabilities);
        response.add("result", result);

        logger.info("Generated initialize response for session {}: {}", sessionId, gson.toJson(response));
        return response;
    }

    private JsonObject handleInitialize(JsonElement id) {
        return handleInitialize(id, null);
    }

    private JsonObject handleRootsList(JsonElement id) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);

        JsonArray roots = new JsonArray();
        JsonObject root1 = new JsonObject();
        root1.addProperty("uri", "/api/craftermcp");
        root1.addProperty("name", "CrafterCMS MCP Root");
        roots.add(root1);

        JsonObject result = new JsonObject();
        result.add("roots", roots);
        response.add("result", result);

        logger.info("Generated roots/list response: {}", gson.toJson(response));
        return response;
    }

    private JsonObject handleResourcesList(JsonElement id) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);

        JsonArray resources = new JsonArray();
        // Add resources here if any

        JsonObject result = new JsonObject();
        result.add("resources", resources);
        response.add("result", result);

        logger.info("Generated resources/list response: {}", gson.toJson(response));
        return response;
    }

    private JsonObject handlePromptsList(JsonElement id) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);

        JsonArray prompts = new JsonArray();
        // Add your prompts here if any

        JsonObject result = new JsonObject();
        result.add("prompts", prompts);
        response.add("result", result);

        logger.info("Generated prompts/list response: {}", gson.toJson(response));
        return response;
    }

    private JsonObject handleNotificationsList(JsonElement id) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);

        JsonArray notifications = new JsonArray();
        
        // Add available notification types
        JsonObject rootsNotification = new JsonObject();
        rootsNotification.addProperty("method", "notifications/roots/listChanged");
        rootsNotification.addProperty("description", "Sent when the list of roots changes");
        notifications.add(rootsNotification);

        JsonObject result = new JsonObject();
        result.add("notifications", notifications);
        response.add("result", result);

        logger.info("Generated notifications/list response: {}", gson.toJson(response));
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

    private JsonObject handleSubscribe(JsonElement id, JsonObject params, String sessionId) {
        if (sessionId == null) {
            return createErrorResponse(id, -32602, "Session ID required for streaming");
        }

        JsonArray events = params.has("events") ? params.get("events").getAsJsonArray() : new JsonArray();
        Set<String> eventSet = new HashSet<>();
        events.each { event -> eventSet.add(event.getAsString()) };

        if (eventSet.isEmpty()) {
            eventSet.add("all");
        }

        subscriptions.put(sessionId, eventSet);

        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        JsonObject result = new JsonObject();
        result.addProperty("subscriptionId", sessionId);
        response.add("result", result);

        logger.info("Subscription created: {} for events: {}", sessionId, eventSet);
        return response;
    }

    private JsonObject handleUnsubscribe(JsonElement id, JsonObject params, String sessionId) {
        if (sessionId == null) {
            return createErrorResponse(id, -32602, "Session ID required for streaming");
        }

        subscriptions.remove(sessionId);

        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        response.add("result", new JsonObject());

        logger.info("Subscription removed: {}", sessionId);
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
        sessions.clear(); // Clear sessions on shutdown
    }

    private boolean isSubscribed(String subscriptionId, JsonObject event) {
        String eventType = event.has("method") ? event.get("method").getAsString().split("/")[0] :
                          (event.has("event") ? event.get("event").getAsString() : "");
        Set<String> subscribedEvents = subscriptions.get(subscriptionId);
        return subscribedEvents != null && (subscribedEvents.contains("all") || subscribedEvents.contains(eventType));
    }
}