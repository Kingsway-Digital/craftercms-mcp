@Grab(group='io.modelcontextprotocol.sdk', module='mcp', version='0.10.0', initClass=false, systemClassLoader=true)
@Grab(group='org.springframework.ai', module='spring-ai-client-chat', version='1.0.0', initClass=false, systemClassLoader=true)
@Grab(group='org.springframework.ai', module='spring-ai-mcp', version='1.0.0', initClass=false, systemClassLoader=true)
@Grab(group='org.springframework.ai', module='spring-ai-openai', version='1.0.0', initClass=false, systemClassLoader=true)
@Grab(group='com.fasterxml.jackson.core', module='jackson-databind', version='2.17.2', initClass=false)
@Grab(group='io.projectreactor', module='reactor-core', version='3.6.0', initClass=false)
@Grab(group='org.eclipse.jetty', module='jetty-server', version='11.0.24')

import java.time.Duration
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.logging.Logger

import groovy.json.JsonSlurper

import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClient.Builder
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.util.LinkedMultiValueMap

import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.http.client.ClientHttpResponse

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.tool.definition.ToolDefinition

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.ai.model.ApiKey
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
/**
 * Main execution logic
 */
def javaLogger = Logger.getLogger("complete.post")
def jsonSlurper = new JsonSlurper()
def requestBody = jsonSlurper.parseText(request.reader.text)
def query = requestBody.message

// Input validation
if (!query) {
    javaLogger.severe("Message field is missing from request")
    return [error: "Message field is required"]
}

javaLogger.info("Processing query: ${query}")

try {
    // Initialize MCP client
    def mcpClient = buildMcpClient(javaLogger)
    javaLogger.info("MCP client built successfully")
    
    // Initialize MCP client
    def mcpClientInitResult = mcpClient.initialize()
    javaLogger.info("MCP client initialized successfully: ${mcpClientInitResult}")

    // Initialize OpenAI ChatClient with our custom MCP tool provider
    def chatModel = buildOpenAiChatModel()
    def toolCallbackProvider = new CustomMcpToolCallbackProvider(mcpClient, javaLogger)
    
    // FIXED: Use defaultToolCallbacks() instead of defaultTools()
    def chatClient = ChatClient.builder(chatModel)
        .defaultToolCallbacks(toolCallbackProvider)
        .build()

    // Execute chat request
    def chatResponse = chatClient.prompt()
        .user(query)
        .call()
        .content()

    javaLogger.info("Chat response generated successfully: ${chatResponse}")
    return [response: chatResponse]

} catch (Exception e) {
    javaLogger.severe("Error processing request: ${e.message}")
    return [error: "Internal server error: ${e.message}"]
}

/**
 * Build OpenAI Chat Model with proper configuration
 */
def buildOpenAiChatModel() {
    def apiKeyValue = System.getenv("crafter_chatgpt")
    if (!apiKeyValue) {
        throw new IllegalStateException("OpenAI API key not found in environment variable 'crafter_chatgpt'")
    }

    def restClientBuilder = RestClient.builder()
    restClientBuilder.defaultHeaders { headers ->
        headers.set(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate")
        headers.set(HttpHeaders.CONTENT_TYPE, "application/json")
        headers.set(HttpHeaders.ACCEPT, "application/json")
    }

    def webClientBuilder = WebClient.builder()
    def responseErrorHandler = new CustomResponseErrorHandler()
    
    def headers = new LinkedMultiValueMap<String, String>()
    headers.add("Content-Type", "application/json")
    headers.add("Accept", "application/json")

    def openAiApi = OpenAiApi.builder()
        .baseUrl("https://api.openai.com/v1")
        .apiKey(apiKeyValue)
        .completionsPath("/chat/completions")
        .headers(headers)
        .restClientBuilder(restClientBuilder)
        .webClientBuilder(webClientBuilder)
        .responseErrorHandler(responseErrorHandler)
        .build()

    def openAiChatOptions = OpenAiChatOptions.builder()
        .model("gpt-4o-mini")
        .temperature(0.7)
        .maxTokens(1000)
        .build()

    return OpenAiChatModel.builder()
        .openAiApi(openAiApi)
        .defaultOptions(openAiChatOptions)
        .build()
}

/**
 * Build MCP client with synchronous HTTP configuration
 */
def buildMcpClient(javaLogger) {
    def siteId = "mcp"
    def mcpServerUrl = "http://localhost:8080/"
    def previewToken = "CCE-V1#5qFpTjXlyPDsrq5FGMCJSA3oDo1DTgK/qYQXFUBSe1zxHpoZFXf30uWCU6eRgefl"
    
    def objMapper = new ObjectMapper()

    def restClient = RestClient.builder()
        .baseUrl(mcpServerUrl)
        .defaultHeaders { headers ->
            headers.set(HttpHeaders.CONTENT_TYPE, "application/json")
            headers.set(HttpHeaders.ACCEPT, "application/json")
            headers.set("X-Crafter-Site", siteId)
            headers.set("X-Crafter-Preview", previewToken)
        }
        .build()

    return new CustomMcpSyncClient(restClient, objMapper, javaLogger)
}

/**
 * Custom MCP Sync Client that implements synchronous HTTP communication
 */
class CustomMcpSyncClient {
    private final RestClient restClient
    private final ObjectMapper objectMapper
    private final Logger logger
    private boolean initialized = false

    CustomMcpSyncClient(RestClient restClient, ObjectMapper objectMapper, Logger logger) {
        this.restClient = restClient
        this.objectMapper = objectMapper
        this.logger = logger
    }

    def initialize() {
        def request = [
            jsonrpc: "2.0",
            method: "initialize",
            params: [
                clientInfo: [
                    name: "mcp-client",
                    version: "1.0.0"
                ],
                clientCapabilities: [
                    roots: true,
                    sampling: true
                ]
            ],
            id: UUID.randomUUID().toString()
        ]

        logger.info("Sending initialize request: ${objectMapper.writeValueAsString(request)}")
        
        def response = restClient.post()
            .uri("/api/craftermcp/mcp.json")
            .body(request)
            .retrieve()
            .toEntity(Map.class)

        logger.info("Received initialize response: ${objectMapper.writeValueAsString(response.body)}")
        
        if (response.body.error) {
            throw new RuntimeException("Initialize failed: ${response.body.error.message}")
        }
        
        initialized = true
        return response.body.result
    }

    def listTools() {
        if (!initialized) {
            throw new IllegalStateException("Client not initialized")
        }

        def request = [
            jsonrpc: "2.0",
            method: "/api/craftermcp/tools.json",
            params: [:],
            id: UUID.randomUUID().toString()
        ]

        logger.info("Sending listTools request: ${objectMapper.writeValueAsString(request)}")
        
        def response = restClient.post()
            .uri("/api/craftermcp/tools.json")
            .body(request)
            .retrieve()

        def responseObj = response.toEntity(Map.class)

        return responseObj.body.result
    }

    def callTool(String toolName, Map parameters) {

        if (!initialized) {
            throw new IllegalStateException("Client not initialized")
        }

        def request = [
            jsonrpc: "2.0",
            method: "tools/call",
            params: [
                name: toolName,
                arguments: parameters
            ],
            id: UUID.randomUUID().toString()
        ]

        logger.info("Sending callTool request: ${objectMapper.writeValueAsString(request)}")
        
        def response = restClient.post()
            .uri("/api/craftermcp/mcp.json")
            .body(request)
            .retrieve()
            .toEntity(Map.class)

        logger.info("Received callTool response: ${objectMapper.writeValueAsString(response.body)}")
        
        if (response.body.error) {
            throw new RuntimeException("Tool call failed: ${response.body.error.message}")
        }
        
        return response.body.result
    }

    boolean isInitialized() {
        return initialized
    }
}

/**
 * Custom Tool Callback Provider that integrates our MCP client with Spring AI
 */
class CustomMcpToolCallbackProvider implements ToolCallbackProvider {
    private final CustomMcpSyncClient mcpClient
    private final Logger logger

    CustomMcpToolCallbackProvider(CustomMcpSyncClient mcpClient, Logger logger) {
        this.mcpClient = mcpClient
        this.logger = logger
    }

    ToolCallback[] getToolCallbacks() {
        if (!mcpClient.isInitialized()) {
            logger.warning("MCP client not initialized, returning empty tool list")
            return new ToolCallback[0]
        }


        def toolResults = []
        
        try {
            def toolsList = mcpClient.listTools()

            def tools = toolsList.tools ?: []
            
            logger.info("Found ${tools.size()} tools from MCP server")
        
            tools.each { tool ->
                def toolCb = new ClientToolCallback()
                toolCb.name = tool.name
                toolCb.description = tool.description
                toolCb.client = mcpClient
 
                def jsonBuilder = new groovy.json.JsonBuilder(tool.inputSchema)
                toolCb.inputSchema = jsonBuilder.toPrettyString()
                toolResults.add(toolCb)
            }
            
            return toolResults
            
        } catch (Exception e) {
            logger.severe("Error listing MCP tools: ${e.message}")
            return new ToolCallback[0]
        }
    }
}

public class ClientToolCallback implements ToolCallback {

    def name
    def description
    def inputSchema
    def client 

    String getName() {
        return name
    }

    String getDescription() {
        return description ?: "MCP tool: ${name}"
    }

    ToolDefinition getToolDefinition() {
        // Create a basic tool definition from MCP tool schema
        return ToolDefinition.builder()
            .name(name)
            .description(description ?: "MCP tool: ${name}")
            .inputSchema(inputSchema ?: [:])
            .build()
    }

    String call(String arguments) {

        try {            
            // Parse arguments JSON
            def argMap = [:]
            
            if (arguments && arguments.trim()) {
                argMap = new groovy.json.JsonSlurper().parseText(arguments)
            }

            
            def result = client.callTool(name, argMap)
            def response = result.content ?: result.output ?: result.toString()
            
            return response
            
        } catch (Exception e) {
            return "Error calling tool: ${e.message}"
        }
    }
    
}

/**
 * Custom error handler for OpenAI API responses
 */
class CustomResponseErrorHandler implements ResponseErrorHandler {

    boolean hasError(ClientHttpResponse response) throws IOException {
        return response.getStatusCode().is4xxClientError() ||
               response.getStatusCode().is5xxServerError()
    }

    void handleError(ClientHttpResponse response) throws IOException {
        def statusCode = response.getStatusCode()
        def statusText = response.getStatusText()
        
        def errorBody = ""
        try {
            errorBody = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)
        } catch (Exception e) {
            // If we can't read the body, continue with basic error info
        }
        
        def errorMessage = "HTTP ${statusCode.value()} ${statusText}"
        if (errorBody) {
            errorMessage += ": ${errorBody}"
        }
        
        def javaLogger = Logger.getLogger("complete.post")
        javaLogger.severe("OpenAI API error: ${errorMessage}")
        throw new RuntimeException("OpenAI API error: ${errorMessage}")
    }
}