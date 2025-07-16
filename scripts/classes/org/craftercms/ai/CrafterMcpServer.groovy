package org.craftercms.ai

@Grab('com.fasterxml.jackson.core:jackson-databind:2.18.0')
@Grab(group='io.modelcontextprotocol.sdk', module='mcp',              version='0.10.0',   initClass=false)
 
import groovy.json.JsonSlurper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.Tool
import io.modelcontextprotocol.spec.McpSchema.CallToolResult

public class CrafterMcpServer  {
    private final McpHandler mcpHandler = new McpHandler();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final McpAsyncServer mcpServer;
    private final HttpServletSseServerTransportProvider sseTransportProvider;

    public CrafterMcpServer() {
        // Initializing MCP server with McpServerFeatures
        def features = new McpServerFeatures()
                    // .addSyncTool(
                    //     new Tool("handleRequest", "Handles JSON-RPC requests", null),
                    //     (exchange, args) -> {
                    //         def params = args instanceof Map ? args : [:];
                    //         def result = mcpHandler.handleRequest(params.method, params.params, exchange.request, exchange.response);
                    //         return new CallToolResult(result);
                    //     }
                    // )
        this.mcpServer = mcpHandler.server//.features(features).build()

        // Initializing SSE transport provider
        this.sseTransportProvider = new HttpServletSseServerTransportProvider(objectMapper, "/sse");
    }

    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        try {
            def jsonRequest = objectMapper.readValue(req.getReader(), Map);
            if (!jsonRequest.jsonrpc || jsonRequest.jsonrpc != "2.0") {
                resp.sendError(resp, -32600, "Invalid JSON-RPC 2.0 request");
                return;
            }

            String method = jsonRequest.method;
            def params = jsonRequest.params;
            String id = jsonRequest.id;

            //log.info("Received MCP request: method=${method}, id=${id}");

            // Use MCP server to handle the request
            def result = mcpHandler.handleRequest(method, params, req, resp);
            if (id) {
                def response = [jsonrpc: "2.0", id: id, result: result];
                resp.status = HttpServletResponse.SC_OK;
                resp.writer.write(objectMapper.writeValueAsString(response));
            } else {
                // Notification (no response needed)
                resp.status = HttpServletResponse.SC_OK;
            }
        } catch (Exception e) {
            // log.error("Error processing MCP request: ${e.message}", e);
            resp.sendError(resp, -32603, "Internal error: ${e.message}");
        }
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
//        if (req.getPathInfo()?.endsWith("/sse")) {
            resp.setContentType("text/event-stream");
            resp.setCharacterEncoding("UTF-8");
            resp.setHeader("Cache-Control", "no-cache");
            resp.setHeader("Connection", "keep-alive");

            try {
                // Delegate SSE handling to WebMvcSseServerTransport
                sseTransportProvider.handleConnection(req, resp, mcpServer);
            } catch (Exception e) {
                //log.error("Error streaming SSE: ${e.message}", e);
                resp.writer.write("event: error\ndata: Failed to stream response: ${e.message}\n\n");
                resp.writer.flush();
            }
        // } else {
        //     resp.status = HttpServletResponse.SC_NOT_FOUND;
        //     resp.writer.write(objectMapper.writeValueAsString([jsonrpc: "2.0", error: [code: -32601, message: "Method not found"]]));
        // }
    }

    private void sendError(HttpServletResponse resp, int code, String message) {
        def errorResponse = [jsonrpc: "2.0", error: [code: code, message: message]];
        resp.status = HttpServletResponse.SC_BAD_REQUEST;
        resp.writer.write(objectMapper.writeValueAsString(errorResponse));
    }
}