@Grab(group='org.springframework.ai', module='spring-ai-client-chat', version='1.0.0', initClass=false, systemClassLoader=true)
@Grab(group='org.springframework.ai', module='spring-ai-openai', version='1.0.0', initClass=false, systemClassLoader=true)

import groovy.json.JsonSlurper

import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClient.Builder
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.util.LinkedMultiValueMap

import org.springframework.ai.chat.client.ChatClient

import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.http.client.ClientHttpResponse

import org.craftercms.ai.mcp.client.McpSyncClient
import org.craftercms.ai.mcp.client.McpToolCallbackProvider

def jsonSlurper = new JsonSlurper()
def requestBody = jsonSlurper.parseText(request.reader.text)
def query = requestBody.message


if (!query) {
    logger.error("Message field is missing from request")
    return [error: "Message field is required"]
}

logger.info("Processing query: ${query}")

try {
    // Initialize MCP client
    def mcpClient = buildMcpClient()
    
    // Initialize MCP client
    def mcpClientInitResult = mcpClient.initialize()

    // Initialize OpenAI ChatClient with our custom MCP tool provider
    def chatModel = buildOpenAiChatModel()
    def toolCallbackProvider = new McpToolCallbackProvider(mcpClient, logger)
    
    def chatClient = ChatClient.builder(chatModel)
        .defaultToolCallbacks(toolCallbackProvider)
        .build()

    // Execute chat request
    def chatResponse = chatClient.prompt()
        .user(query)
        .call()
        .content()

    logger.info("Chat response generated successfully: ${chatResponse}")
    return [response: chatResponse]

} catch (Exception err) {
    logger.error("Error processing request: ${err.message}")
    return [error: "Internal server error: ${err.message}"]
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
def buildMcpClient() {
    def siteId = "mcp"
    def mcpServerUrl = "http://localhost:8080/"
    def previewToken = "CCE-V1#5qFpTjXlyPDsrq5FGMCJSA3oDo1DTgK/qYQXFUBSe1zxHpoZFXf30uWCU6eRgefl"
    
    def restClient = RestClient.builder()
        .baseUrl(mcpServerUrl)
        .defaultHeaders { headers ->
            headers.set(HttpHeaders.CONTENT_TYPE, "application/json")
            headers.set(HttpHeaders.ACCEPT, "application/json")
            headers.set("X-Crafter-Site", siteId)
            headers.set("X-Crafter-Preview", previewToken)
        }
        .build()

    return new McpSyncClient(restClient)
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
        
        System.out.println("OpenAI API error: ${errorMessage}")
        throw new RuntimeException("OpenAI API error: ${errorMessage}")
    }
}