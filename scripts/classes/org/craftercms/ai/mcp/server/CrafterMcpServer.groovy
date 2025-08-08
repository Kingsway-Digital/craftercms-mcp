package org.craftercms.ai.mcp.server;

@Grab('com.google.code.gson:gson:2.10.1')
@Grab('io.jsonwebtoken:jjwt-api:0.12.6')
@Grab('io.jsonwebtoken:jjwt-impl:0.12.6')
@Grab('io.jsonwebtoken:jjwt-jackson:0.12.6')
@Grab('org.slf4j:slf4j-api:2.0.13')
@Grab('ch.qos.logback:logback-classic:1.5.6')

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ServletException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.Jwks;

import java.util.Base64;

import org.craftercms.ai.mcp.server.tools.*
import org.craftercms.ai.mcp.server.resources.*
import org.craftercms.ai.mcp.server.prompts.*

class CrafterMcpServer {

    private static final Logger logger = LoggerFactory.getLogger(CrafterMcpServer.class);
    private static final Gson gson = new Gson();
    private String serverId;
    private volatile boolean running;
    private LinkedBlockingQueue<JsonObject> streamQueue = new LinkedBlockingQueue<>();
    private Map<String, Set<String>> subscriptions = new ConcurrentHashMap<>();
    private Map<String, String> sessions = new ConcurrentHashMap<>();
    private Map<String, Long> sessionCreationTimes = new ConcurrentHashMap<>();

    private ArrayList<McpTool> mcpTools = new ArrayList<>();
    public ArrayList<McpTool> getMcpTools() { return mcpTools; }
    public void setMcpTools(ArrayList<McpTool> value) { mcpTools = value; }

    private ArrayList<McpResource> mcpResources = new ArrayList<>();
    public ArrayList<McpResource> getMcpResources() { return mcpResources; }
    public void setMcpResources(ArrayList<McpResource> value) { mcpResources = value; }

    private ArrayList<McpResourceTemplate> mcpResourceTemplates = new ArrayList<>();
    public ArrayList<McpResourceTemplate> getMcpResourceTemplates() { return mcpResourceTemplates; }
    public void setMcpResourceTemplates(ArrayList<McpResourceTemplate> value) { mcpResourceTemplates = value; }

    private ArrayList<McpPrompt> mcpPrompts = new ArrayList<>();
    public ArrayList<McpPrompt> getMcpPrompts() { return mcpPrompts; }
    public void setMcpPrompts(ArrayList<McpPrompt> value) { mcpPrompts = value; }


    // Inner class for credential translation
    class CredentialTranslator {
        String translateCredentials(String userId, String[] scopes, McpTool tool) {
            switch (tool.getAuthType()) {
                case "api_key":
                    String apiKey = tool.getAuthConfig().get("apiKey");
                    return apiKey != null ? apiKey : "default-api-key";
                case "basic_auth":
                    String username = userId;
                    String password = tool.getAuthConfig().get("password");
                    String credentials = username + ":" + password;
                    return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
                case "custom_header":
                    String headerName = tool.getAuthConfig().get("headerName");
                    String headerValue = tool.getAuthConfig().get("headerValue");
                    return headerValue != null ? headerValue : userId;
                default:
                    logger.warn("Unsupported auth type for tool {}: {}", tool.getToolName(), tool.getAuthType());
                    return null;
            }
        }
    }

    CrafterMcpServer() {
        this.serverId = UUID.randomUUID().toString();
        this.running = true;
        this.mcpTools = new ArrayList<>();
        this.mcpResources = new ArrayList<>();
        this.mcpResourceTemplates = new ArrayList<>();
        this.mcpPrompts = new ArrayList<>();
    }

    void doOptionsStreaming(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, Mcp-Session-Id");
        resp.setHeader("Access-Control-Expose-Headers", "Mcp-Session-Id");
        resp.setStatus(HttpServletResponse.SC_OK);
        logger.info("Handled OPTIONS preflight request");
    }

    void doOAuthGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            resp.setHeader("Access-Control-Allow-Origin", "*");

            JsonObject metadata = new JsonObject();
            metadata.addProperty("resource", "https://api.example.com/mcp");
            JsonArray authServers = new JsonArray();
            authServers.add("https://auth.example.com");
            metadata.add("authorization_servers", authServers);
            metadata.addProperty("bearer_methods_supported", "header");
            metadata.addProperty("jwks_uri", "https://auth.example.com/.well-known/jwks.json");

            try (PrintWriter out = resp.getWriter()) {
                out.print(gson.toJson(metadata));
                out.flush();
            }
            logger.info("Served OAuth protected resource metadata");
    }

    void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!running) {
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            sendError(resp, null, -32000, "Server is shutting down");
            return;
        }

        String authHeader = req.getHeader("Authorization");

        if(authHeader.startsWith("X-Crafter-Preview")) {
            // studio
            logger.info("MCP client connecting to preview server");
        }
        else {
            String[] userInfo = validateAccessToken(authHeader, resp);
            if (userInfo == null) {
                return;
            }
            String userId = userInfo[0];
            String[] scopes = userInfo[1] != null ? userInfo[1].split(" ") : new String[0];
            logger.info("Validated user: {}", userId);

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
            JsonObject response = handleRequest(jsonString, null, userId, scopes);
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

        String authHeader = req.getHeader("Authorization");
        String previewTokenHeader = req.getHeader("X-Crafter-Preview");
         
        String[] userInfo = null

        if(authHeader == null && previewTokenHeader !=null) {

            logger.info("MCP client connecting to preview server");
            userInfo = "this is an admin user and these words will be scopes"

        }
        else {        
           userInfo = validateAccessToken(authHeader, resp);
        }

        if (userInfo == null) {
            return;
        }

        String userId = userInfo[0];
        String[] scopes = userInfo[1] != null ? userInfo[1].split(" ") : new String[0];
        logger.info("Received Authorization header: {}", authHeader);

        String acceptHeader = req.getHeader("Accept");
        logger.info("Received Accept header: {}", acceptHeader);

        String existingSessionId = req.getHeader("Mcp-Session-Id");
        logger.info("Received Mcp-Session-Id header: {}", existingSessionId);

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

        boolean isInitializeRequest = false;
        String sessionId = existingSessionId;

        try {
            JsonObject request = gson.fromJson(jsonString, JsonObject.class);
            String method = request.has("method") ? request.get("method").getAsString() : "";
            isInitializeRequest = "initialize".equals(method);

            if (isInitializeRequest) {
                sessionId = UUID.randomUUID().toString();
                sessions.put(sessionId, serverId);
                sessionCreationTimes.put(sessionId, System.currentTimeMillis());
                logger.info("Created new session: {} for initialize request", sessionId);
            } else if (existingSessionId == null || !sessions.containsKey(existingSessionId)) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                sendError(resp, null, -32002, "Invalid session: Missing or invalid Mcp-Session-Id header");
                return;
            }
        } catch (Exception e) {
            logger.warn("Failed to parse request: {}", e.getMessage());
            sendError(resp, null, -32700, "Parse error: " + e.getMessage());
            return;
        }

        resp.setCharacterEncoding("UTF-8");
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
            JsonObject response = handleRequest(jsonString, sessionId, userId, scopes);
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
            sendError(resp, null, -32000, "Server error: {}", e.getMessage());
        }
    }

    void doGetStreaming(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!running) {
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            logger.warn("Streaming GET request rejected: Server is shutting down");
            return;
        }

        String authHeader = req.getHeader("Authorization");
        String[] userInfo = validateAccessToken(authHeader, resp);
        if (userInfo == null) {
            return;
        }
        String userId = userInfo[0];
        String[] scopes = userInfo[1] != null ? userInfo[1].split(" ") : new String[0];
        logger.info("Validated user: {}", userId);

        String existingSessionId = req.getHeader("Mcp-Session-Id");
        if (existingSessionId == null || !sessions.containsKey(existingSessionId)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            logger.warn("GET streaming request without valid session");
            return;
        }

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Connection", "close");
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Mcp-Session-Id", existingSessionId);

        logger.info("Handling streamable HTTP GET for session: {}", existingSessionId);

        try (PrintWriter out = resp.getWriter()) {
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

    private String[] validateAccessToken(String authHeader, HttpServletResponse resp) throws IOException {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.warn("No valid Authorization header received");
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setHeader("WWW-Authenticate", "Bearer realm=\"example\", error=\"missing_token\", " +
                "error_description=\"Authorization header missing or invalid\", " +
                "authorization_uri=\"https://auth.example.com/oauth/authorize\", " +
                "discovery_uri=\"https://auth.example.com/.well-known/oauth-authorization-server\"");
            return null;
        }

        String token = authHeader.substring(7);
        try {
            String jwksUri = "https://auth.example.com/.well-known/jwks.json";
            // Fetch JWKS using HttpClient
            HttpClient client = HttpClient.newBuilder().build();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(jwksUri))
                .GET()
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Failed to fetch JWKS: HTTP " + response.statusCode());
            }
            String jwksJson = response.body();

            // Parse JWT to get the kid
            JwsHeader header = Jwts.parserBuilder()
                .build()
                .parse(token)
                .getHeader();
            String kid = header.getKeyId();
            if (kid == null) {
                throw new JwtException("Missing key ID in JWT header");
            }

            // Parse JWKS and find the matching JWK
            JsonObject jwks = gson.fromJson(jwksJson, JsonObject.class);
            JsonArray keys = jwks.getAsJsonArray("keys");
            Jwk<?> jwk = null;
            for (JsonElement key : keys) {
                Jwk<?> candidate = Jwks.parser().build().parse(key.toString());
                if (kid.equals(candidate.getId())) {
                    jwk = candidate;
                    break;
                }
            }
            if (jwk == null) {
                throw new JwtException("No JWK found for kid: " + kid);
            }

            // Validate JWT
            Jws<Claims> claims = Jwts.parserBuilder()
                .setSigningKey(jwk.getKey())
                .build()
                .parseClaimsJws(token);

            if (!claims.getBody().getAudience().contains("https://api.example.com/mcp")) {
                logger.warn("Invalid token audience");
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                resp.setHeader("WWW-Authenticate", "Bearer realm=\"example\", error=\"invalid_token\", " +
                    "error_description=\"Invalid audience\"");
                return null;
            }
            if (claims.getBody().getExpiration().before(new java.util.Date())) {
                logger.warn("Token expired");
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                resp.setHeader("WWW-Authenticate", "Bearer realm=\"example\", error=\"invalid_token\", " +
                    "error_description=\"Token expired\"");
                return null;
            }

            String userId = claims.getBody().getSubject();
            String scopes = claims.getBody().get("scope", String.class);
            return new String[]{userId, scopes};
        } catch (JwtException | IllegalArgumentException e) {
            logger.error("Token validation failed: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setHeader("WWW-Authenticate", "Bearer realm=\"example\", error=\"invalid_token\", " +
                "error_description=\"Token validation failed\"");
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching JWKS: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return null;
        }
    }

    private JsonObject handleRequest(String jsonInput, String sessionId, String userId, String[] scopes) {
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

            logger.info("Processing JSON-RPC method: {}, id: {}, session: {}, user: {}", method, id, sessionId, userId);

            switch (method) {
                case "initialize":
                    return handleInitialize(id, sessionId);
                case "tools/list":
                    return handleToolsList(id);
                case "tools/call":
                    return handleToolCall(id, params, userId, scopes);
                case "roots/list":
                    return handleRootsList(id);
                case "resources/list":
                    return handleResourcesList(id);
                case "resources/templates/list":
                    return handleResourceTemplatesList(id);
                case "prompts/get":
                    return handlePromptsGet(id, params);
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
                case "ping":
                    return handlePing(id, sessionId);
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
        JsonObject tools = new JsonObject();
        tools.addProperty("listChanged", false);
        capabilities.add("tools", tools);
        JsonObject resources = new JsonObject();
        resources.addProperty("subscribe", false);
        resources.addProperty("listChanged", false);
        capabilities.add("resources", resources);
        JsonObject prompts = new JsonObject();
        prompts.addProperty("listChanged", false);
        capabilities.add("prompts", prompts);
        JsonObject roots = new JsonObject();
        roots.addProperty("listChanged", true);
        capabilities.add("roots", roots);
        result.add("capabilities", capabilities);
        response.add("result", result);

        logger.info("Generated initialize response for session {}: {}", sessionId, gson.toJson(response));
        return response;
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
        for (McpResource resource : mcpResources) {
            JsonObject resourceObj = new JsonObject();
            resourceObj.addProperty("uri", resource.uri);
            resourceObj.addProperty("name", resource.name);
            resources.add(resourceObj);
        }

        JsonObject result = new JsonObject();
        result.add("resources", resources);
        response.add("result", result);

        logger.info("Generated resources/list response: {}", gson.toJson(response));
        return response;
    }

    private JsonObject handleResourceTemplatesList(JsonElement id) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);

        JsonArray templates = new JsonArray();
        for (McpResourceTemplate template : mcpResourceTemplates) {
            JsonObject templateObj = new JsonObject();
            templateObj.addProperty("uriTemplate", template.uriTemplate);
            templateObj.addProperty("name", template.name);
            templates.add(templateObj);
        }

        JsonObject result = new JsonObject();
        result.add("resourceTemplates", templates);
        response.add("result", result);

        logger.info("Generated resources/templates/list response: {}", gson.toJson(response));
        return response;
    }

    private JsonObject handlePromptsList(JsonElement id) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);

        JsonArray prompts = new JsonArray();
        for (McpPrompt prompt : mcpPrompts) {
            JsonObject promptObj = new JsonObject();
            promptObj.addProperty("promptTemplate", prompt.promptTemplate);
            promptObj.addProperty("name", prompt.name);
            prompts.add(promptObj);
        }

        JsonObject result = new JsonObject();
        result.add("prompts", prompts);
        response.add("result", result);

        logger.info("Generated prompts/list response: {}", gson.toJson(response));
        return response;
    }

    private JsonObject handlePromptsGet(JsonElement id, JsonObject params) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);

        String promptName = params.has("name") ? params.get("name").getAsString() : null;
        if (promptName == null) {
            return createErrorResponse(id, -32602, "Missing prompt name");
        }

        McpPrompt prompt = mcpPrompts.stream().filter(p -> p.name.equals(promptName)).findFirst().orElse(null);
        if (prompt == null) {
            return createErrorResponse(id, -32602, "Prompt not found: " + promptName);
        }

        JsonObject result = new JsonObject();
        result.addProperty("promptTemplate", prompt.promptTemplate);
        result.addProperty("name", prompt.name);
        response.add("result", result);

        logger.info("Generated prompts/get response: {}", gson.toJson(response));
        return response;
    }

    private JsonObject handleNotificationsList(JsonElement id) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);

        JsonArray notifications = new JsonArray();
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
        for (McpTool mcpToolRecord : mcpTools) {
            JsonObject currentTool = new JsonObject();
            currentTool.addProperty("name", mcpToolRecord.getToolName());
            currentTool.addProperty("description", mcpToolRecord.getToolDescription());

            JsonObject inputSchema = new JsonObject();
            inputSchema.addProperty("type", "object");

            JsonObject properties = new JsonObject();
            for (McpTool.ToolParam param : mcpToolRecord.getParams()) {
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

    private JsonObject handleToolCall(JsonElement id, JsonObject params, String userId, String[] scopes) {
        if (!params.has("name") || params.get("name").isJsonNull()) {
            return createErrorResponse(id, -32602, "Missing tool name");
        }
        String toolName = params.get("name").getAsString();
        JsonObject arguments = params.has("arguments") ? params.get("arguments").getAsJsonObject() : new JsonObject();

        logger.info("Calling tool: {} with arguments: {} for user: {}", toolName, gson.toJson(arguments), userId);

        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);

        McpTool toolToCall = mcpTools.stream().filter(t -> t.getToolName().equals(toolName)).findFirst().orElse(null);
        if (toolToCall == null) {
            return createErrorResponse(id, -32602, "Invalid tool: " + toolName);
        }

        if (toolToCall.getRequiredScopes() != null && !Arrays.asList(scopes).containsAll(Arrays.asList(toolToCall.getRequiredScopes()))) {
            return createErrorResponse(id, -32602, "Insufficient permissions for tool: " + toolName);
        }

        CredentialTranslator translator = new CredentialTranslator();
        String toolCredentials = translator.translateCredentials(userId, scopes, toolToCall);
        if (toolCredentials == null) {
            return createErrorResponse(id, -32000, "Tool authentication failed: " + toolName);
        }

        List<String> callArgs = new ArrayList<>();
        for (McpTool.ToolParam arg : toolToCall.getParams()) {
            if (!arguments.has(arg.name)) {
                return createErrorResponse(id, -32602, "Missing argument: " + arg.name);
            }
            String argValue = arguments.get(arg.name).getAsString().replaceAll("\"", "");
            callArgs.add(argValue);
        }
        callArgs.add(toolCredentials);

        String toolResponse = toolToCall.call(callArgs);

        JsonArray content = new JsonArray();
        JsonObject textContent = new JsonObject();
        textContent.addProperty("type", "text");
        textContent.addProperty("text", toolResponse);
        content.add(textContent);

        JsonObject result = new JsonObject();
        result.add("content", content);
        response.add("result", result);

        logger.info("Generated tool/call response: {}", gson.toJson(response));
        return response;
    }

    private JsonObject handleSubscribe(JsonElement id, JsonObject params, String sessionId) {
        if (sessionId == null) {
            return createErrorResponse(id, -32602, "Session ID required for streaming");
        }

        JsonArray events = params.has("events") ? params.get("events").getAsJsonArray() : new JsonArray();
        Set<String> eventSet = new HashSet<>();
        for (JsonElement event : events) {
            eventSet.add(event.getAsString());
        }

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
        sessions.clear();
        sessionCreationTimes.clear();
    }

    private boolean isSubscribed(String subscriptionId, JsonObject event) {
        String eventType = event.has("method") ? event.get("method").getAsString().split("/")[0] :
                          (event.has("event") ? event.get("event").getAsString() : "");
        Set<String> subscribedEvents = subscriptions.get(subscriptionId);
        return subscribedEvents != null && (subscribedEvents.contains("all") || subscribedEvents.contains(eventType));
    }

    private JsonObject handlePing(JsonElement id, String sessionId) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        response.add("result", new JsonObject());

        logger.info("Generated Ping response for session {}: {}", sessionId, gson.toJson(response));
        return response;
    }

    private void cleanupStaleSessions() {
        long currentTime = System.currentTimeMillis();
        sessionCreationTimes.entrySet().removeIf(entry ->
            (currentTime - entry.getValue()) > TimeUnit.HOURS.toMillis(1));
        sessions.keySet().retainAll(sessionCreationTimes.keySet());
        subscriptions.keySet().retainAll(sessionCreationTimes.keySet());
    }
}