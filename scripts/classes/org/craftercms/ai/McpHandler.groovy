package org.craftercms.ai

@Grab(group='io.modelcontextprotocol.sdk', module='mcp', version='0.10.0', initClass=false)
@Grab('com.fasterxml.jackson.core:jackson-databind:2.17.2')
@Grab('org.slf4j:slf4j-api:2.0.16')



import groovy.util.logging.Slf4j
import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.server.McpAsyncServer
import io.modelcontextprotocol.server.McpServerFeatures
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.concurrent.ConcurrentHashMap
import io.modelcontextprotocol.spec.McpSchema.Tool
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult
import io.modelcontextprotocol.spec.McpSchema.PromptMessage
import io.modelcontextprotocol.spec.McpSchema.Role
import io.modelcontextprotocol.spec.McpSchema.TextContent
import io.modelcontextprotocol.util.DeafaultMcpUriTemplateManagerFactory
import io.modelcontextprotocol.spec.McpSchema
import io.modelcontextprotocol.spec.McpSchema.ListResourcesResult
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult
//import io.modelcontextprotocol.spec.McpSchema.ListToolsRequest
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
//import io.modelcontextprotocol.spec.McpSchema.TextContentBlock
//import io.modelcontextprotocol.spec.McpSchema.ListPromptsRequest
import io.modelcontextprotocol.spec.McpSchema.ListPromptsResult
//import io.modelcontextprotocol.spec.McpSchema.ListResourcesResult
//import io.modelcontextprotocol.spec.McpSchema.ListResourcesRequest
import java.util.concurrent.CompletableFuture
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider
import java.time.Duration

@Slf4j
class McpHandler {
    private final ObjectMapper objectMapper = new ObjectMapper()
    private final Map<String, String> sessions = new ConcurrentHashMap<>()
    private final McpAsyncServer server
    private final List<McpServerFeatures.SyncToolSpecification> tools = []

    McpHandler() {
        // Initialize McpServer
        def messageEndpoint = "/api/craftercms/mcp"
        def sseEndpoint = "/api/craftermcp/sse.json"
        def objMapper = new ObjectMapper()
        def transportProvider = new HttpServletSseServerTransportProvider(objMapper, messageEndpoint, sseEndpoint)
        def uriTemplateManagerFactory = new DeafaultMcpUriTemplateManagerFactory()
        def features = new MyMcpServerFeatures()

        server = new McpAsyncServer(transportProvider, objMapper, features, Duration.ofSeconds(10), uriTemplateManagerFactory) 

        // Register a sample tool
        def toolSpec = new McpServerFeatures.SyncToolSpecification(
            new Tool("calculator", "Basic calculator", [
                operation: "string",
                a: "number",
                b: "number"
            ]),
            { exchange, request ->
                def args = request.arguments()
                String operation = args.operation
                double a = args.a as double
                double b = args.b as double
                def result = operation == "add" ? a + b : (operation == "subtract" ? a - b : null)
                if (result == null) throw new IllegalArgumentException("Unsupported operation: ${operation}")
                return new CallToolResult("Calculation result: ${result}", result)
            }
        )
        tools.add(toolSpec)

        // Register methods with the server
        server.registerMethod("initialize", this.&handleInitialize)
        server.registerMethod("tool/call", this.&handleToolCall)
        server.registerMethod("prompt/get", this.&handlePromptGet)
    }

    void handleRequest(HttpServletRequest req, HttpServletResponse resp) {
        try {
            // Manual JSON-RPC request handling
            def requestBody = req.reader.text
            def requestJson = objectMapper.readValue(requestBody, Map)
            def method = requestJson.method
            def params = requestJson.params
            def id = requestJson.id

            def result = server.handleRequest(method, params)
            resp.contentType = "application/json"
            resp.writer.write(objectMapper.writeValueAsString([
                jsonrpc: "2.0",
                result: result,
                id: id
            ]))
            resp.writer.flush()
        } catch (Exception e) {
            log.error("Error handling request", e)
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.contentType = "application/json"
            resp.writer.write(objectMapper.writeValueAsString([
                jsonrpc: "2.0",
                error: [code: -32603, message: "Internal error: ${e.message}"],
                id: null
            ]))
            resp.writer.flush()
        }
    }

    void handleSseEvent(HttpServletRequest req, HttpServletResponse resp, String sessionId) {
        if (!sessions.containsKey(sessionId)) {
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            resp.writer.write("event: error\ndata: Invalid session: ${sessionId}\n\n")
            resp.writer.flush()
            return
        }

        try {
            // Start SSE response
            resp.setContentType("text/event-stream")
            resp.setCharacterEncoding("UTF-8")
            resp.setHeader("Cache-Control", "no-cache")
            resp.setHeader("Connection", "keep-alive")
            
            // Simulate streaming tool results
            def toolResult = new CallToolResult("Sample streamed result", "Streaming data")
            resp.writer.write("event: tool_result\ndata: ${objectMapper.writeValueAsString(toolResult)}\n\n")
            resp.writer.flush()
            Thread.sleep(500)
            resp.writer.write("event: complete\ndata: Stream completed\n\n")
            resp.writer.flush()
        } catch (Exception e) {
            log.error("Error streaming SSE event", e)
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.writer.write("event: error\ndata: Error streaming: ${e.message}\n\n")
            resp.writer.flush()
        }
    }

    private def handleInitialize(def params) {
        String sessionId = UUID.randomUUID().toString()
        sessions.put(sessionId, "active")
        log.info("Initialized MCP session: ${sessionId}")
        return [
            capabilities: [
                tools: tools.collect { [name: it.tool.name, description: it.tool.description, parameters: it.tool.parameters] },
                prompts: [[name: "greeting", description: "A friendly greeting prompt"]],
                resources: [[uri: "example://resource", name: "Example Resource"]]
            ],
            sessionId: sessionId
        ]
    }

    private def handleToolCall(def params) {
        String toolName = params.tool
        def args = params.arguments
        def toolSpec = tools.find { it.tool.name == toolName }
        if (!toolSpec) {
            throw new IllegalArgumentException("Tool not found: ${toolName}")
        }
        log.info("Executing tool: ${toolName} with args: ${args}")
        return toolSpec.callback.call(null, [arguments: args])
    }

    private def handlePromptGet(def params) {
        String promptName = params.name
        if (promptName == "greeting") {
            String name = params.arguments?.name ?: "friend"
            return new GetPromptResult(
                "A personalized greeting message",
                [new PromptMessage(Role.USER, new TextContent("Hello ${name}! How can I assist you today?"))]
            )
        }
        throw new IllegalArgumentException("Prompt not found: ${promptName}")
    }
}


class MyMcpServerFeatures implements McpServerFeatures.Async {
    @Override
    CompletableFuture<ListToolsResult> listTools(ListToolsRequest request) {
        def tools = [/* Define tools here */]
        CompletableFuture.completedFuture(new ListToolsResult(tools: tools))
    }

    @Override
    CompletableFuture<CallToolResult> callTool(CallToolRequest request) {
        def content = ["Tool executed: ${request.params?.name}"]
        CompletableFuture.completedFuture(new CallToolResult(content: content))
    }

    @Override
    CompletableFuture<ListPromptsResult> listPrompts(ListPromptsRequest request) {
        def prompts = [/* Define prompts here */]
        CompletableFuture.completedFuture(new ListPromptsResult(prompts: prompts))
    }

    @Override
    CompletableFuture<GetPromptResult> getPrompt(GetPromptRequest request) {
        def messages = [new Message(
            role: "user",
            content: "Prompt: ${request.params?.name}"
        )]
        CompletableFuture.completedFuture(new GetPromptResult(messages: messages))
    }

    @Override
    CompletableFuture<ListResourcesResult> listResources(ListResourcesRequest request) {
        def resources = [/* Define resources here */]
        CompletableFuture.completedFuture(new ListResourcesResult(resources: resources))
    }

    @Override
    CompletableFuture<McpSchema.ExecuteResourceResult> executeResource(McpSchema.ExecuteResourceRequest request) {
        def data = new McpSchema.ExecuteResourceResult.Data(
            forecast: [new McpSchema.ExecuteResourceResult.Data.Forecast(
                date: "2025-07-11",
                temperature: 25,
                condition: "Sunny"
            )]
        )
        CompletableFuture.completedFuture(new McpSchema.ExecuteResourceResult(data: data))
    }
}