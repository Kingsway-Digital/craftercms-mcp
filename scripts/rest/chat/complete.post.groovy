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

import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j

import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClient.Builder
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.util.LinkedMultiValueMap

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

import org.springframework.web.client.RestClient
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.http.client.ClientHttpResponse

import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider

import org.springframework.ai.chat.client.ChatClient

import io.modelcontextprotocol.client.McpAsyncClient
import io.modelcontextprotocol.client.McpClientFeatures
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport

import io.modelcontextprotocol.spec.McpSchema
import reactor.core.publisher.Mono
import java.util.function.Function

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.ai.model.ApiKey

/**
 * Main execution logic
 */
def jsonSlurper = new JsonSlurper()
def requestBody = jsonSlurper.parseText(request.reader.text)
def query = requestBody.message

// Input validation
if (!query) {
    logger.error("Message field is missing from request")
    return [error: "Message field is required"]
}

logger.info("Processing query: ${query}")

try {
    // Initialize MCP client
    def asyncClient = buildMcpClient(logger)
    logger.info("MCP client built successfully")
    
    // Initialize MCP client with timeout handling
    def mcpClientInitResult = asyncClient.initialize()
        .timeout(Duration.ofSeconds(30))
        .block()
    logger.info("MCP client initialized successfully")

    // Initialize OpenAI ChatClient
    def chatModel = buildOpenAiChatModel()
    def toolCallbackProvider = new AsyncMcpToolCallbackProvider(asyncClient)
    
    def chatClient = ChatClient.builder(chatModel)
        .defaultTools(toolCallbackProvider)
        .build()

    // Execute chat request
    def chatResponse = chatClient.prompt()
        .user(query)
        .call()
        .content()

    logger.info("Chat response generated successfully")
    return [response: chatResponse]

} catch (Exception e) {
    logger.error("Error processing request: ${e.message}", e)
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
 * Build MCP client with proper configuration
 */
def buildMcpClient(logger) {
    def siteId = "mcp"
    def mcpServerUrl = "http://localhost:8080"
    def sseEndpoint = "/api/craftermcp/sse.json"
    def previewToken = "CCE-V1#5qFpTjXlyPDsrq5FGMCJSA3oDo1DTgK/qYQXFUBSe1zxHpoZFXf30uWCU6eRgefl"

    def httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    def requestBuilder = HttpRequest.newBuilder()
        .uri(URI.create(mcpServerUrl + sseEndpoint))
        .header("X-Crafter-Site", siteId)
        .header("X-Crafter-Preview", previewToken)
        .header("Accept", "application/json") // As per your comment about Crafter REST API requirements
        .timeout(Duration.ofSeconds(30))

    def objMapper = new ObjectMapper()

    def transport = new HttpClientSseClientTransport(
        httpClient, requestBuilder, mcpServerUrl, sseEndpoint, objMapper)

    def clientInfo = new McpSchema.Implementation("mcp-client", "1.0.0")

    def clientCapabilities = McpSchema.ClientCapabilities.builder()
        .roots(true)
        .sampling()
        .build()

    def roots = [:]
    def samplingHandler = { req -> 
        Mono.just(new McpSchema.CreateMessageResult("Sample response")) 
    } as Function

    def mcpFeatures = new McpClientFeatures.Async(
        clientInfo, 
        clientCapabilities, 
        roots, 
        null, null, null, null, 
        samplingHandler
    )

    return new McpAsyncClient(
        transport, 
        Duration.ofSeconds(30), 
        Duration.ofSeconds(30), 
        mcpFeatures
    )
}

/**
 * Custom error handler for OpenAI API responses
 */
class CustomResponseErrorHandler implements ResponseErrorHandler {

    @Override
    boolean hasError(ClientHttpResponse response) throws IOException {
        return response.getStatusCode().is4xxClientError() || 
               response.getStatusCode().is5xxServerError()
    }

    @Override
    void handleError(ClientHttpResponse response) throws IOException {
        def statusCode = response.getStatusCode()
        def statusText = response.getStatusText()
        
        // Read error body for detailed error information
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
        
        logger.error("OpenAI API error: ${errorMessage}")
        throw new RuntimeException("OpenAI API error: ${errorMessage}")
    }
}