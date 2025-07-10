@GrabResolver(name='spring-snapshot', root='https://repo.spring.io/snapshot', m2Compatible=true)
@GrabResolver(name='maven-central', root='https://repo1.maven.org/maven2', m2Compatible=true)

@Grab(group='org.springframework.ai', module='spring-ai-core', version='1.0.0-M6', initClass=false)
@Grab(group='org.springframework.ai', module='spring-ai-openai', version='1.0.0-M6', initClass=false)
@Grab(group='io.modelcontextprotocol.sdk', module='mcp', version='0.10.0', initClass=false)
@Grab(group='com.fasterxml.jackson.core', module='jackson-databind', version='2.17.2', initClass=false)

import java.time.Duration

import groovy.json.JsonSlurper

import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.ai.chat.client.ChatClient
import io.modelcontextprotocol.client.McpAsyncClient
import io.modelcontextprotocol.client.McpClientFeatures

//import io.modelcontextprotocol.client.Client
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport

import io.modelcontextprotocol.spec.McpSchema
import io.modelcontextprotocol.schema.*
import reactor.core.publisher.Mono
import java.util.function.Function

def jsonSlurper = new JsonSlurper()
def query = jsonSlurper.parseText(request.reader.text).message

if (!query) {
    logger.error("Message field is missing")
    return [error: "Message field is required"]
}

def asyncClient

try {
    // Initialize OpenAI ChatClient
    def apiKey = System.getenv("crafter_chatgpt")
    
    def openAiApi = new OpenAiApi("https://api.openai.com", apiKey)
    def openAiChatOptions = OpenAiChatOptions.builder().model("gpt-4o-mini").build()
    def chatModel = new OpenAiChatModel(openAiApi, openAiChatOptions)
    def chatClient = ChatClient.builder(chatModel).build()

 
    // Instantiate McpAsyncClient with HttpClientSseClientTransport
    def mcpPreviewToken = "this may get difficult" 
    def mcpServerUrl = "http://localhost:8080/api/craftercms/mcp"
    def clientInfo = new McpSchema.Implementation("mcp-client", "1.0.0")
    def clientCapabilities = McpSchema.ClientCapabilities.builder()
        .roots(true)
        .sampling()
        .build()
//        .prompts(true)

//        .resources(true)

        //        .tools(true)

    def roots = [:] // Empty map; add roots if needed, e.g., ["file:///path": new Root("file:///path", "Local files")]
    
    def toolsChangeConsumers = null // [
//        { tools -> logger.info("Tools updated: ${tools.collect { it.name }}"); Mono.empty() } as Function
  //  ]
    
    def resourcesChangeConsumers  = null //= [
    //    { resources -> logger.info("Resources updated: ${resources.collect { it.name }}"); Mono.empty() } as Function
    //]
    
    def promptsChangeConsumers  = null //= [
      //  { prompts -> logger.info("Prompts updated: ${prompts.collect { it.name }}"); Mono.empty() } as Function
    //]
    
    def loggingConsumers  = null //= [
      //  { logMsg -> logger.info("Server log: ${logMsg.data}"); Mono.empty() } as Function
    //]
 
    def samplingHandler = { req -> Mono.just(new McpSchema.CreateMessageResult("Sample response")) } as Function

    def mcpFeatures = new McpClientFeatures.Async(
        clientInfo,
        clientCapabilities,
        roots,
        toolsChangeConsumers,
        resourcesChangeConsumers,
        promptsChangeConsumers,
        loggingConsumers,
        samplingHandler
    )
				
    def transport = new HttpClientSseClientTransport(mcpServerUrl)
    
    asyncClient = new McpAsyncClient(transport, Duration.ofSeconds(10), Duration.ofSeconds(10), mcpFeatures)
    asyncClient.initialize()

    // Log available tools
    def tools = asyncClient.listTools().get()
    logger.info("MCP tools: ${tools.tools?.collect { it.name } ?: 'None'}")

    // Process prompt
    def response
    if (query.toLowerCase().startsWith("weather")) {
        def city = query.split(/\s+/).drop(1).join(" ") ?: "London"
        def toolRequest = [name: "getWeatherForecastByLocation", arguments: [city: city]]
        def toolResult = asyncClient.callToolAsync(toolRequest).get()
        response = toolResult.content?.find { it.type == "text" }?.text ?: "No weather data"
    } else {
        response = "Non-weather prompts not supported"
    }

    def chatResponse = chatClient.prompt().user(query).call().content()

    return [response: chatResponse]
} catch (Exception e) {
    logger.error("Error: ${e.message}", e)
    return [error: e.message]
} finally {
    asyncClient?.close()
}