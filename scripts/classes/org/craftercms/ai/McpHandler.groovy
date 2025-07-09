package org.craftercms.ai

import groovy.util.logging.Slf4j
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.ai.mcp.McpServerFeatures

@Slf4j
class McpHandler {
    private final ObjectMapper objectMapper = new ObjectMapper()
    private final Map<String, Closure> toolCallbacks = [:]
    private final Map<String, String> sessions = new ConcurrentHashMap<>()
    private final List<McpServerFeatures.SyncToolSpecification> tools = []

    McpHandler() {
        // Register a sample tool
        def toolSpec = new McpServerFeatures.SyncToolSpecification(
            new McpSchema.Tool("calculator", "Basic calculator", [
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
                return new McpServerFeatures.ToolResult("Calculation result: ${result}", result)
            }
        )
        tools.add(toolSpec)
    }

    def handleRequest(String method, def params, HttpServletRequest req, HttpServletResponse resp) {
        switch (method) {
            case "initialize":
                return handleInitialize(params)
            case "tool/call":
                return handleToolCall(params)
            case "prompt/get":
                return handlePromptGet(params)
            default:
                throw new IllegalArgumentException("Method not found: ${method}")
        }
    }

    private def handleInitialize(def params) {
        String sessionId = UUID.randomUUID().toString()
        sessions.put(sessionId, "active")
        log.info("Initialized MCP session: ${sessionId}")
        return [
            jsonrpc: "2.0",
            result: [
                capabilities: [
                    tools: tools.collect { [name: it.tool.name, description: it.tool.description, parameters: it.tool.parameters] },
                    prompts: [[name: "greeting", description: "A friendly greeting prompt"]],
                    resources: [[uri: "example://resource", name: "Example Resource"]]
                ],
                sessionId: sessionId
            ]
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
            return new McpServerFeatures.GetPromptResult(
                "A personalized greeting message",
                [new McpServerFeatures.PromptMessage(McpServerFeatures.Role.USER, new McpServerFeatures.TextContent("Hello ${name}! How can I assist you today?"))]
            )
        }
        throw new IllegalArgumentException("Prompt not found: ${promptName}")
    }

    void handleSseEvent(HttpServletRequest req, HttpServletResponse resp, String sessionId) {
        if (!sessions.containsKey(sessionId)) {
            resp.writer.write("event: error\ndata: Invalid session: ${sessionId}\n\n")
            resp.writer.flush()
            return
        }
        // Simulate streaming tool results
        def toolResult = new McpServerFeatures.ToolResult("Sample streamed result", "Streaming data")
        resp.writer.write("event: tool_result\ndata: ${objectMapper.writeValueAsString(toolResult)}\n\n")
        resp.writer.flush()
        Thread.sleep(500)
        resp.writer.write("event: complete\ndata: Stream completed\n\n")
        resp.writer.flush()
    }
}