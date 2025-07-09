import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.mcp.client.McpClient
import org.springframework.ai.mcp.client.McpSyncClientCustomizer
import org.springframework.boot.web.servlet.ServletRegistrationBean
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.StandardEnvironment
import org.springframework.web.context.support.GenericWebApplicationContext
import org.springframework.web.servlet.DispatcherServlet
import org.springframework.web.servlet.mvc.method.RequestMappingInfo
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping


def apiKey = System.getenv("crafter_chatgpt")

def query = params.question

if(!query) {
    return "Error: 'question' field is required"
}

// Define OpenAiChatModel
def chatModel = new OpenAiChatModel( apiKey, OpenAiChatOptions.builder().withModel('gpt-4o-mini').build() )

// Define McpSyncClientCustomizer
def mcpClientCustomizer = new McpSyncClientCustomizer() {
    void customize(String serverConfigurationName, McpClient.SyncSpec spec) {
        spec.requestTimeout(Duration.ofSeconds(30))
        spec.headers(['Content-Type': 'text/event-stream'])
    }
}

// Define McpClient
def sseConnections = [
    'mcp-server': [
        url: 'http://localhost:8080'
    ]
]

def mcpClient = new McpClient( toolCallbackEnabled: true, sseConnections: sseConnections )

// Define ToolCallbackProvider
def toolCallbackProvider = new ToolCallbackProvider()

// Define ChatClient
def chatClientBuilder = ChatClient.builder(chatModel)

def chatClient = chatClientBuilder.defaultToolCallbacks([toolCallbackProvider]).build()

return chatClient.prompt().user(query).call().content()




 