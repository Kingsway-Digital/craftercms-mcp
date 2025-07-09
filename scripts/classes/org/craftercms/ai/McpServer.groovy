package org.craftercms.ai

@GrabResolver(name='spring-milestones', root='https://repo.spring.io/milestone')
@Grab('org.apache.groovy:groovy:4.0.23')
@Grab('org.slf4j:slf4j-api:2.0.16')
@Grab('ch.qos.logback:logback-classic:1.5.8')
@Grab('jakarta.servlet:jakarta.servlet-api:6.0.0')
@Grab('com.fasterxml.jackson.core:jackson-databind:2.18.0')
@Grab('org.springframework.experimental:mcp-webmvc-sse-transport:0.9.0')
@Grab('org.springframework:spring-webmvc:6.1.14')

import groovy.util.logging.Slf4j
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.experimental.mcp.webmvc.McpWebMvcSseTransportProvider

private final McpHandler mcpHandler = new McpHandler()
private final ObjectMapper objectMapper = new ObjectMapper()
private final McpWebMvcSseTransportProvider sseTransportProvider = new McpWebMvcSseTransportProvider()

public class McpServer {
    
    public void doPost(HttpServletRequest req, HttpServletResponse resp) {
        resp.setContentType("application/json")
        resp.setCharacterEncoding("UTF-8")
    
        try {
            def jsonRequest = objectMapper.readValue(req.getReader(), Map)
            if (!jsonRequest.jsonrpc || jsonRequest.jsonrpc != "2.0") {
                sendError(resp, -32600, "Invalid JSON-RPC 2.0 request")
                return
            }
    
            String method = jsonRequest.method
            def params = jsonRequest.params
            String id = jsonRequest.id
    
            log.info("Received MCP request: method=${method}, id=${id}")
    
            def result = mcpHandler.handleRequest(method, params, req, resp)
            if (id) {
                def response = [jsonrpc: "2.0", id: id, result: result]
                resp.status = HttpServletResponse.SC_OK
                resp.writer.write(objectMapper.writeValueAsString(response))
            } else {
                // Notification (no response needed)
                resp.status = HttpServletResponse.SC_OK
            }
        } catch (Exception e) {
            log.error("Error processing MCP request: ${e.message}", e)
            sendError(resp, -32603, "Internal error: ${e.message}")
        }
    }
    
    public void doGet(HttpServletRequest req, HttpServletResponse resp) {
        if (req.getPathInfo()?.endsWith("/sse")) {
            resp.setContentType("text/event-stream")
            resp.setCharacterEncoding("UTF-8")
            resp.setHeader("Cache-Control", "no-cache")
            resp.setHeader("Connection", "keep-alive")
    
            try {
                sseTransportProvider.handleSseStream(req, resp, mcpHandler.&handleSseEvent)
            } catch (Exception e) {
                log.error("Error streaming SSE: ${e.message}", e)
                resp.writer.write("event: error\ndata: Failed to stream response: ${e.message}\n\n")
                resp.writer.flush()
            }
        } else {
            resp.status = HttpServletResponse.SC_NOT_FOUND
            resp.writer.write(objectMapper.writeValueAsString([jsonrpc: "2.0", error: [code: -32601, message: "Method not found"]]))
        }
    }
    
    public void sendError(HttpServletResponse resp, int code, String message) {
        def errorResponse = [jsonrpc: "2.0", error: [code: code, message: message]]
        resp.status = HttpServletResponse.SC_BAD_REQUEST
        resp.writer.write(objectMapper.writeValueAsString(errorResponse))
    }
}