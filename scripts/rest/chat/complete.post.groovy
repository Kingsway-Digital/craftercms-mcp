
@GrabResolver(name='custom', root='https://repo.spring.io/snapshot', m2Compatible='true')
@Grab(group='org.springframework.ai', module='spring-ai-core', version='1.0.0-SNAPSHOT', initClass=false)
@Grab(group='org.springframework.ai', module='spring-ai-openai', version='1.0.0-SNAPSHOT', initClass=false)
@Grab(group='org.springframework.ai', module='spring-ai-mcp', version='1.0.0-SNAPSHOT', initClass=false)
@Grab(group='org.springframework.ai', module='spring-ai-mcp-client-spring-boot-starter', version='1.0.0-SNAPSHOT', initClass=false)



import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
//import org.springframework.ai.mcp.client.McpClient

import org.springframework.ai.chat.client.ChatClient
// import org.springframework.ai.chat.client.advisor.api.Advisor
// import org.springframework.ai.chat.client.advisor.api.AdvisorContext
// import org.springframework.ai.chat.client.advisor.api.AdvisorResponse
// import org.springframework.ai.chat.client.advisor.api.ChatClientAdvisor

def apiKey = System.getenv("crafter_chatgpt")

def query = params.question

if(!query) {
    return "Error: 'question' field is required"
}

/*

// Define OpenAiChatModel
def chatModel = new OpenAiChatModel( apiKey, OpenAiChatOptions.builder().withModel('gpt-4o-mini').build() )

// // Define McpSyncClientCustomizer
// def mcpClientCustomizer = new McpSyncClientCustomizer() {
//     void customize(String serverConfigurationName, McpClient.SyncSpec spec) {
//         spec.requestTimeout(Duration.ofSeconds(30))
//         spec.headers(['Content-Type': 'text/event-stream'])
//     }
// }

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


class ToolCallbackProvider implements ChatClientAdvisor {

    AdvisorResponse advise(AdvisorContext context, Advisor chain) {
        def tools = [
            [
                type: 'function',
                function: [
                    name: 'get_weather',
                    description: 'Get the current weather for a location',
                    parameters: [
                        type: 'object',
                        properties: [
                            location: [
                                type: 'string',
                                description: 'The city and state, e.g., New York, NY'
                            ]
                        ],
                        required: ['location']
                    ]
                ]
            ]
        ]
        context.chatClient().prompt().tools(tools)
        chain.advise(context)
    }
}

*/
 