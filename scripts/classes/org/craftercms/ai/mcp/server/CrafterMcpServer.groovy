package org.craftercms.ai.mcp.server;

@Grab('com.google.code.gson:gson:2.10.1')

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ServletException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CrafterMcpServer {

    private static final Logger logger = LoggerFactory.getLogger(CrafterMcpServer.class);
    private static final Gson gson = new Gson();
    private String serverId;
    private volatile boolean running;
    private ArrayList<McpTool> mcpTools = [];
    private LinkedBlockingQueue<JsonObject> streamQueue = new LinkedBlockingQueue<>();

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

        StringBuilder jsonInput = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                jsonInput.append(line);
            }
        }

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        try (PrintWriter out = resp.getWriter()) {
            logger.info("Received POST request: $jsonInput");
            handleRequest(jsonInput.toString(), out, false);
        } catch (Exception e) {
            logger.error("Server error: ${e.getMessage()}");
            sendError(resp, null, -32000, "Server error: ${e.getMessage()}");
        }
    }

    // Streaming HTTP POST
    void doPostStreaming(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!running) {
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            sendError(resp, null, -32000, "Server is shutting down");
            return;
        }

        // Set headers for SSE
        resp.setContentType("text/event-stream");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache");
        resp.setHeader("Connection", "keep-alive");

        StringBuilder jsonInput = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                jsonInput.append(line);
            }
        }

        try (PrintWriter out = resp.getWriter()) {
            logger.info("Received streaming POST request: $jsonInput");
            handleRequest(jsonInput.toString(), out, true);
            
            // Keep stream alive for events
            while (running) {
                JsonObject event = streamQueue.poll(1, TimeUnit.SECONDS);
                if (event != null) {
                    out.write("data: ${gson.toJson(event)}\n\n");
                    out.flush();
                }
            }
        } catch (Exception e) {
            logger.error("Streaming error: ${e.getMessage()}");
        }
    }

    // Regular HTTP GET
    void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!running) {
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            logger.warn("GET request rejected: Server is shutting down");
            return;
        }

        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        logger.warn("Invalid GET request path: ${req.getServletPath()}");
    }

    // Streaming HTTP GET
    void doGetStreaming(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!running) {
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            logger.warn("Streaming GET request rejected: Server is shutting down");
            return;
        }

        // Set headers for SSE
        resp.setContentType("text/event-stream");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache");
        resp.setHeader("Connection", "keep-alive");

        try (PrintWriter out = resp.getWriter()) {
            // Send initial connection confirmation
            JsonObject initEvent = new JsonObject();
            initEvent.addProperty("event", "connection");
            initEvent.addProperty("status", "connected");
            out.write("data: ${gson.toJson(initEvent)}\n\n");
            out.flush();

            // Stream events
            while (running) {
                JsonObject event = streamQueue.poll(1, TimeUnit.SECONDS);
                if (event != null) {
                    out.write("data: ${gson.toJson(event)}\n\n");
                    out.flush();
                }
            }
        } catch (Exception e) {
            logger.error("Streaming GET error: ${e.getMessage()}");
        }
    }

    private void handleRequest(String jsonInput, PrintWriter out, boolean isStreaming) {
        try {
            JsonObject request = gson.fromJson(jsonInput, JsonObject.class);
            String method = request.get("method").getAsString();
            JsonElement id = request.get("id");
            JsonObject params = request.has("params") ? request.get("params").getAsJsonObject() : new JsonObject();

            logger.info("Processing JSON-RPC method: $method, id: $id");

            switch (method) {
                case "initialize":
                    handleInitialize(id, out, isStreaming);
                    break;
                case "tools/list":
                    handleToolsList(id, out, isStreaming);
                    break;
                case "tools/call":
                    handleToolCall(id, params, out, isStreaming);
                    break;
                case "events/check":
                    handleEventsCheck(id, out, isStreaming);
                    break;
                case "shutdown":
                    handleShutdown(id, out, isStreaming);
                    break;
                default:
                    sendError(out, id, -32601, "Method not found: $method", isStreaming);
            }
        } catch (Exception e) {
            logger.error("Parse error: ${e.getMessage()}");
            sendError(out, null, -32700, "Parse error: ${e.getMessage()}", isStreaming);
        }
    }

    private void handleInitialize(JsonElement id, PrintWriter out, boolean isStreaming) {
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
        result.add("capabilities", capabilities);
        response.add("result", result);

        sendResponse(out, response, isStreaming);
        logger.info("Sent initialize response: ${gson.toJson(response)}");
    }

    private void handleToolsList(JsonElement id, PrintWriter out, boolean isStreaming) {
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

        sendResponse(out, response, isStreaming);
    }

    private void handleToolCall(JsonElement id, JsonObject params, PrintWriter out, boolean isStreaming) {
        String toolName = params.get("name").getAsString();
        JsonObject arguments = params.has("arguments") ? params.get("arguments").getAsJsonObject() : new JsonObject();
        
        logger.info("Calling tool: $toolName with arguments: ${gson.toJson(arguments)}");
        
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);

        McpTool toolToCall = null;
        mcpTools.each { toolRecord ->
            if(toolRecord.toolName.equals(toolName)) {
                toolToCall = toolRecord;
            }
        }

        if(!toolToCall) {
            sendError(out, id, -32602, "Invalid tool: $toolName", isStreaming);
            return;
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

        sendResponse(out, response, isStreaming);
        logger.info("Sent tool call response: ${gson.toJson(response)}");
    }

    private void handleEventsCheck(JsonElement id, PrintWriter out, boolean isStreaming) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        response.add("result", new JsonObject());
        sendResponse(out, response, isStreaming);
    }

    private void handleShutdown(JsonElement id, PrintWriter out, boolean isStreaming) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        response.add("result", new JsonObject());
        sendResponse(out, response, isStreaming);
        shutdown();
    }

    private void sendResponse(PrintWriter out, JsonObject response, boolean isStreaming) {
        String jsonResponse = gson.toJson(response);
        if (isStreaming) {
            out.write("data: $jsonResponse\n\n");
            out.flush();
        } else {
            out.println(jsonResponse);
        }
        if (!isStreaming) {
            streamQueue.offer(response); // Add to stream queue for streaming clients
        }
    }

    private void sendError(PrintWriter out, JsonElement id, int code, String message, boolean isStreaming) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        if (id != null) {
            response.add("id", id);
        }
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        response.add("error", error);
        
        sendResponse(out, response, isStreaming);
        logger.warn("Sent error response: code=$code, message=$message");
    }

    private void sendError(HttpServletResponse resp, JsonElement id, int code, String message) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            sendError(out, id, code, message, false);
        }
    }

    private void shutdown() {
        running = false;
        logger.info("Server shut down");
        // Notify streaming clients
        JsonObject shutdownEvent = new JsonObject();
        shutdownEvent.addProperty("event", "shutdown");
        shutdownEvent.addProperty("message", "Server is shutting down");
        streamQueue.offer(shutdownEvent);
    }
}