@GrabResolver(name='spring-snapshot', root='https://repo.spring.io/snapshot', m2Compatible=true)
@GrabResolver(name='maven-central', root='https://repo1.maven.org/maven2', m2Compatible=true)

@Grab(group='org.springframework.ai', module='spring-ai-core', version='1.0.0-M6', initClass=false)
@Grab(group='org.springframework.ai', module='spring-ai-openai', version='1.0.0-M6', initClass=false)
@Grab(group='io.modelcontextprotocol.sdk', module='mcp', version='0.10.0', initClass=false)
@Grab(group='com.fasterxml.jackson.core', module='jackson-databind', version='2.17.2', initClass=false)
@Grab(group='org.slf4j', module='slf4j-api', version='2.0.16', initClass=false)
@Grab(group='org.slf4j', module='slf4j-simple', version='2.0.16', initClass=false)

import groovy.util.logging.Slf4j
import groovy.json.JsonSlurper
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.ai.chat.client.ChatClient
import io.modelcontextprotocol.client.McpClient
//import io.modelcontextprotocol.client.Client
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport

@Slf4j
def jsonSlurper = new JsonSlurper()
def query = jsonSlurper.parseText(request.reader.text).message

if (!query) {
    log.error("Message field is missing")
    return [error: "Message field is required"]
}

try {
    // Initialize OpenAI ChatClient
    def apiKey = System.getenv("crafter_chatgpt")
    def openAiApi = new OpenAiApi("https://api.openai.com", apiKey)
    def openAiChatOptions = OpenAiChatOptions.builder().withModel("gpt-4o-mini").build()
    def chatModel = new OpenAiChatModel(openAiApi, openAiChatOptions)
    def chatClient = ChatClient.builder(chatModel).build()

    // Initialize McpClient with StdioClientTransport
    def mcpClient = new Client([name: "mcp-client", version: "1.0.0"])
    mcpClient.connect(new HttpClientSseClientTransport())
    mcpClient.initialize()

    // Log available MCP tools
    def tools = mcpClient.listTools()
    log.info("Available MCP tools: ${tools.tools?.collect { it.name } ?: 'No tools found'}")

    // Process prompt
    def response
    if (query.toLowerCase().startsWith("weather")) {
        def city = query.split(/\s+/).drop(1).join(" ") ?: "London"
        def toolRequest = [name: "getWeatherForecastByLocation", arguments: [city: city]]
        def toolResult = mcpClient.callTool(toolRequest)
        response = toolResult.content?.find { it.type == "text" }?.text ?: "No weather data available"
    } else {
        response = chatClient.prompt().user(query).call().content()
    }

    return [response: response]
} catch (Exception e) {
    log.error("Error: ${e.message}", e)
    return [error: e.message]
}