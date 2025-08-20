@Grab(group='org.springframework.ai', module='spring-ai-client-chat', version='1.0.0', initClass=false, systemClassLoader=true)
@Grab(group='org.springframework.ai', module='spring-ai-openai', version='1.0.0', initClass=false, systemClassLoader=true)

import groovy.json.JsonSlurper

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor

import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi

import org.springframework.util.LinkedMultiValueMap

import org.springframework.http.HttpHeaders
import org.springframework.http.client.ClientHttpResponse

import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClient.Builder
import org.springframework.web.client.DefaultResponseErrorHandler

import org.springframework.web.reactive.function.client.WebClient

import org.craftercms.ai.mcp.client.McpSyncClient
import org.craftercms.ai.mcp.client.McpToolCallbackProvider

def openAIKey = System.getenv("crafter_chatgpt")

def jsonSlurper = new JsonSlurper()
def requestBody = jsonSlurper.parseText(request.reader.text)
def query = requestBody.message
def siteId = siteContext.siteName

// For now, lets assume that we're getting a preview token to the same server we're running on - this makes it easier to share the project
// Ultimately, any connection information should come from the config
// siteConfig.getString("ai.crafterPreviewToken")
def previewToken = getCookieValue(request, "crafterPreview")
System.out.println("-----------------> " + previewToken)    


if (!query) {
    logger.error("Message field is missing from request")
    return [error: "Message field is required"]
}

logger.info("Processing query: ${query}")

try {
    // Initialize MCP client
    def mcpClient = buildMcpClient(siteId, previewToken, request)
    
    // Initialize MCP client
    def mcpClientInitResult = mcpClient.initialize()

    // Initialize OpenAI ChatClient with our custom MCP tool provider
    System.out.println("${openAIKey}")
    def chatModel = buildOpenAiChatModel(openAIKey)
    def toolCallbackProvider = new McpToolCallbackProvider(mcpClient)
    
    System.out.println(chatModel)
    // Add our MCP server tools provider to the chat client
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
def buildOpenAiChatModel(openAIKey) {

    if (!openAIKey) {
        throw new IllegalStateException("OpenAI API key not found in environment variable 'crafter_chatgpt'")
    }

    def restClientBuilder = RestClient.builder()
    restClientBuilder.defaultHeaders { headers ->
        headers.set(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate")
        headers.set(HttpHeaders.CONTENT_TYPE, "application/json")
        headers.set(HttpHeaders.ACCEPT, "application/json")
    }

    def webClientBuilder = WebClient.builder()
    def responseErrorHandler = new DefaultResponseErrorHandler()
    
    def headers = new LinkedMultiValueMap<String, String>()
    headers.add("Content-Type", "application/json")
    headers.add("Accept", "application/json")

    def openAiApi = OpenAiApi.builder()
        .baseUrl("https://api.openai.com/v1")
        .apiKey(openAIKey)
        .completionsPath("/chat/completions")
        // .headers(headers)
        .restClientBuilder(restClientBuilder)
        .webClientBuilder(webClientBuilder)
        .responseErrorHandler(responseErrorHandler)
        .build()

    // adjust model params as needed
    def openAiChatOptions = OpenAiChatOptions.builder()
        .model("o4-mini-2025-04-16")
        //.temperature(0.7)
        //.maxTokens(1000)
        .build()

    return OpenAiChatModel.builder()
        .openAiApi(openAiApi)
        .defaultOptions(openAiChatOptions)
        .build()
}

/**
 * Build MCP client with synchronous HTTP configuration
 * Assumes mcp server is a crafter mcp server on the same machine and handles preview 
 */
def buildMcpClient(currentSiteId, previewToken, request) {
    def siteId = currentSiteId
    def serverScheme = request.getScheme()
    def serverName = request.getServerName()
    def serverPort = request.getServerPort()
    def mcpServerUrl = "$serverScheme://$serverName:$serverPort/"

    def restClient = RestClient.builder()
        .baseUrl(mcpServerUrl)
        .defaultHeaders { headers ->
            headers.set(HttpHeaders.CONTENT_TYPE, "application/json")
            headers.set(HttpHeaders.ACCEPT, "application/json")
            headers.set("X-Crafter-Site", siteId)

            if(modePreview) {
                headers.set("X-Crafter-Preview", previewToken)
            }
            else {
                headers.set("Authorization", "Bearer: TheWheelsOnTheBusGoRoundAndRound-AndRoundAndRoundAndRoundAndRound")
            }
        }
        .build()

    return new McpSyncClient(restClient)
}

def getCookieValue(request, name) {
    if (request.getCookies() != null) {
        for (def cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue()
            }
        }
    }
    return null; // not found
}