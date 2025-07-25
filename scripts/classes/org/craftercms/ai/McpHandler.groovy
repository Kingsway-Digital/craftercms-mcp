package org.craftercms.ai

@Grab(group='io.modelcontextprotocol.sdk', module='mcp',              version='0.10.0',   initClass=false)
@Grab(group='com.fasterxml.jackson.core',  module='jackson-databind', version='2.17.2',   initClass=false)
@Grab('org.slf4j:slf4j-api:2.0.16')

import java.util.ArrayList
import java.util.HashMap
import java.util.List
import java.util.Map
import java.util.function.BiConsumer
import java.util.function.BiFunction
import io.modelcontextprotocol.spec.McpSchema
import io.modelcontextprotocol.util.Assert
import io.modelcontextprotocol.util.Utils
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import groovy.util.logging.Slf4j
import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.server.McpAsyncServer
import io.modelcontextprotocol.server.McpServerFeatures
import io.modelcontextprotocol.server.McpServerFeatures.Async
import io.modelcontextprotocol.server.McpServerFeatures.AsyncResourceSpecification
import io.modelcontextprotocol.server.McpServerFeatures.AsyncPromptSpecification
import io.modelcontextprotocol.server.McpAsyncServerExchange
import io.modelcontextprotocol.server.McpSyncServerExchange
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.concurrent.ConcurrentHashMap
import io.modelcontextprotocol.server.McpServerFeatures
import io.modelcontextprotocol.server.McpServerFeatures.Async
import io.modelcontextprotocol.util.DeafaultMcpUriTemplateManagerFactory
import io.modelcontextprotocol.util.McpUriTemplateManagerFactory
import io.modelcontextprotocol.spec.McpSchema.Tool
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult
import io.modelcontextprotocol.spec.McpSchema.PromptMessage
import io.modelcontextprotocol.spec.McpSchema.Role
import io.modelcontextprotocol.spec.McpSchema.TextContent
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities
import io.modelcontextprotocol.spec.McpSchema.Implementation
import java.util.concurrent.CompletableFuture
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider
import io.modelcontextprotocol.server.McpServer
import io.modelcontextprotocol.server.McpSyncServer
import java.time.Duration

class McpHandler {
    private final ObjectMapper objectMapper = new ObjectMapper()
    private final Map<String, String> sessions = new ConcurrentHashMap<>()
    public final McpAsyncServer server
    private final List<McpServerFeatures.SyncToolSpecification> tools = []

    McpHandler() {
        // Initialize McpServer
        def messageEndpoint = "/api/craftercms/mcp"
        def sseEndpoint = "/api/craftermcp/sse.json"
        def objMapper = new ObjectMapper()
        def transportProvider = HttpServletSseServerTransportProvider.builder()
               .baseUrl("http://localhost:8080")
               .messageEndpoint(messageEndpoint).build()

        //     def toolSpec = new McpServerFeatures.SyncToolSpecification(
        def sampleTool = new Tool("calculator", "Basic calculator", SAMPLE_TOOL_SCHEMA)
        def serverCapabilities = ServerCapabilities.builder().tools(true).build()
		server = McpServer.async(transportProvider)
			.serverInfo("test-server", "1.0.0")
			.capabilities(serverCapabilities)
            .tool(sampleTool, (exchange, args) -> Mono.just(new CallToolResult(List.of(), false)))
			.build();
       
        // Register methods with the server
        // server.registerMethod("initialize", this.&handleInitialize)
        // server.registerMethod("tool/call", this.&handleToolCall)
        // server.registerMethod("prompt/get", this.&handlePromptGet)
        //server.initialize()
    }

    void handleRequest(HttpServletRequest req, HttpServletResponse resp) {

             System.out.println("HANDLE REQUEST")
    
        try {
            // Manual JSON-RPC request handling
 System.out.println("X")
            def requestBody = req.reader.text
            def requestJson = objectMapper.readValue(requestBody, Map)
            def method = requestJson.method
            def params = requestJson.params
            def id = requestJson.id
 System.out.println("Y")

            def result =  INITIALIZED_MESSAGE//server.handleRequest(method, params)
 System.out.println("Z")

            resp.contentType = "application/json"
            resp.writer.write(objectMapper.writeValueAsString([
                jsonrpc: "2.0",
                result: result,
                id: id
            ]))
 System.out.println("Z1")

            resp.writer.flush()
        } catch (Exception e) {
            System.out.println("Error handling request"+e)
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
             System.out.println("SSE EVENT")

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
            
            def toolResult = ""
            try {
                def map = [:]
                map.a = "k"
                toolResult = new CallToolResult("Sample streamed result", false)
            }catch(err) {toolResult = err}

          System.out.println("SSE :Response")

//             resp.writer.write("message: result\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":${objectMapper.writeValueAsString(toolResult)}}\n\n")
//             resp.writer.flush()
//   //          Thread.sleep(500)

        def INIT_EVENT = "event: initialize\n" +
                 "data: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocol_version\":\"2024-11-05\",\"capabilities\":{\"tools\":true,\"resources\":true,\"prompts\":true},\"server_info\":{\"name\":\"my-server\",\"version\":\"1.0.0\"}}}\n\n"

        resp.writer.write(INIT_EVENT)

        def ENDPOINT_EVENT = "event: endpoint\n" +
                     "data: http://localhost:8080/api/craftermcp/mcp.json\n\n"

        resp.writer.write(ENDPOINT_EVENT)

        def ENDPOINT = "{    \"jsonrpc\": \"2.0\",    \"id\": 1,    \"result\": {      \"protocolVersion\": \"2024-11-05\",      \"capabilities\": {        \"logging\": {},        \"prompts\": {          \"listChanged\": true        },        \"resources\": {          \"subscribe\": true,          \"listChanged\": true        },        \"tools\": {          \"listChanged\": true        }      },      \"serverInfo\": {        \"name\": \"ExampleServer\",        \"title\": \"Example Server Display Name\",        \"version\": \"1.0.0\"      },      \"instructions\": \"Optional instructions for the client\"    }  }\n\n"           
                
        resp.writer.write(ENDPOINT)
       
       
        def TOOLS_LIST_EVENT = "event: tools/list\n" +
                       "data: {\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"tools\":[{\"name\":\"search\",\"description\":\"Search API\",\"parameters\":{\"city\":\"string\"}},{\"name\":\"calculator\",\"description\":\"Math calculations\",\"parameters\":{\"expression\":\"string\"}},{\"name\":\"database_query\",\"description\":\"Query database\",\"parameters\":{\"query\":\"string\"}}]}}\n\n"

        resp.writer.write(TOOLS_LIST_EVENT)


        def CAPABILITIES = "{    \"jsonrpc\": \"2.0\",    \"id\": 1,    \"result\": {      \"protocolVersion\": \"2024-11-05\",      \"capabilities\": {        \"logging\": {},        \"prompts\": {          \"listChanged\": true        },        \"resources\": {          \"subscribe\": true,          \"listChanged\": true        },        \"tools\": {          \"listChanged\": true        }      },      \"serverInfo\": {        \"name\": \"ExampleServer\",        \"title\": \"Example Server Display Name\",        \"version\": \"1.0.0\"      },      \"instructions\": \"Optional instructions for the client\"    }  }\n\n"           
                
                resp.writer.write(CAPABILITIES)

            // resp.writer.write("message: done\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"result\":\"success\",\"isError\":false}}\n\n")
            resp.writer.flush()
        } catch (Exception e) {
            //log.error("Error streaming SSE event", e)
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.writer.write("message: error\ndata: Error streaming: ${e.message}\n\n")
            resp.writer.flush()
        }
    }

    private def handleInitialize(params) {
        String sessionId = UUID.randomUUID().toString()
        sessions.put(sessionId, "active")
        //log.info("Initialized MCP session: ${sessionId}")
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
             System.out.println("TOOL CALL")
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
             System.out.println("GET PROMPT")

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

    def INITIALIZED_MESSAGE = "{    \"jsonrpc\": \"2.0\",    \"id\": 1,    \"result\": {      \"protocolVersion\": \"2025-03-26\",      \"capabilities\": {        \"logging\": {},        \"prompts\": {          \"listChanged\": true        },        \"resources\": {          \"subscribe\": true,          \"listChanged\": true        },        \"tools\": {          \"listChanged\": true        }      },      \"serverInfo\": {        \"name\": \"ExampleServer\",        \"version\": \"1.0.0\"      },      \"instructions\": \"Optional instructions for the client\"    }  }"


    def SAMPLE_TOOL_SCHEMA = "{ "+
       " \"name\": \"query_database\",   "+
       " \"title\": \"Database Query Tool\",   "+
       " \"description\": \"Queries a database for records matching the given criteria\",   "+
       " \"inputSchema\": {   "+
       "   \"type\": \"object\",   "+
       "   \"properties\": {   "+
       "   \"query\": {   "+
       "      \"type\": \"string\",   "+
       "      \"description\": \"SQL query string to execute\"   "+
       "    },   "+
       "    \"limit\": {   "+
       "      \"type\": \"integer\",   "+
       "      \"description\": \"Maximum number of records to return\",   "+
       "      \"minimum\": 1,   "+
       "      \"maximum\": 100   "+
       "    }   "+
       "  },   "+
       "  \"required\": [\"query\"]   "+
       "},   "+
       "\"outputSchema\": {   "+
       "  \"type\": \"object\",   "+
       "  \"properties\": {   "+
       "    \"result\": {   "+
       "      \"type\": \"array\",   "+
       "      \"items\": {   "+
       "        \"type\": \"object\",   "+
       "        \"properties\": {   "+
       "          \"id\": {   "+
       "            \"type\": \"string\",   "+
       "            \"description\": \"Record ID\"   "+
       "          },   "+
       "          \"data\": {   "+
       "            \"type\": \"object\",   "+
       "            \"description\": \"Record data\"   "+
       "          }   "+
       "        },   "+
       "        \"required\": [\"id\"]   "+
       "      },   "+
       "      \"description\": \"List of matching records\"   "+
       "    },   "+
       "    \"isError\": {   "+
       "      \"type\": \"boolean\",   "+
       "      \"description\": \"Indicates if the query resulted in an error\"   "+
       "    },   "+
       "    \"errorMessage\": {   "+
       "      \"type\": \"string\",   "+
       "      \"description\": \"Error message if isError is true\"   "+
       "    }   "+   
       "  },   "+
       "  \"required\": [\"result\", \"isError\"]   "+
       "}   "+
       "}"
}



