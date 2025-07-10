@GrabResolver(name='spring-milestones', root='https://repo.spring.io/milestone', m2Compatible=true)
@GrabResolver(name='maven-central', root='https://repo1.maven.org/maven2', m2Compatible=true)


@Grab(group='org.springframework', module='spring-core', version='7.0.0-M6')
@Grab(group='org.springframework.ai', module='spring-ai-starter-mcp-client-webflux', version='1.0.0-M7', initClass=false)
@Grab(group='org.springframework.ai', module='spring-ai-starter-model-openai', version='1.0.0-M7', initClass=false)
@Grab(group='org.springframework.experimental', module='mcp', version='0.4.0', initClass=false)

@Grab(group='org.springframework.experimental', module='mcp-webmvc-sse-transport', version='0.6.0', initClass=false)
@Grab(group='org.springframework.experimental', module='mcp-webflux-sse-transport', version='0.6.0', initClass=false)
@Grab(group='org.springframework.ai', module='spring-ai-mcp', version='1.0.0-M7', initClass=false)
@Grab(group='org.springframework', module='spring-webmvc', version='6.1.14', initClass=false)
@Grab(group='org.springframework', module='spring-context', version='6.1.14', initClass=false)
@Grab(group='jakarta.servlet', module='jakarta.servlet-api', version='5.0.0', initClass=false)
@Grab(group='com.fasterxml.jackson.core', module='jackson-databind', version='2.17.2', initClass=false)
@Grab(group='org.slf4j', module='slf4j-api', version='2.0.16', initClass=false)
@Grab(group='org.slf4j', module='slf4j-simple', version='2.0.16', initClass=false)

// @GrabResolver(name='spring-milestones', root='https://repo.spring.io/milestone', m2Compatible=true)
// @GrabResolver(name='spring-milestones', root='https://repo.spring.io/libs-milestone-local', m2Compatible=true)
// @GrabResolver(name='spring-snapshots', root='https://repo.spring.io/snapshot')
// @GrabResolver(name='maven-central', root='https://repo1.maven.org/maven2', m2Compatible=true)

// @Grab('org.springframework.boot:spring-boot-starter-web:3.2.5')
// @Grab('org.springframework.ai:spring-ai-starter-model-openai:1.0.0-M1')
// @Grab('org.springframework.ai:spring-ai-starter-mcp-client-webflux:1.0.0-M1')

// @Grab(group='org.springframework', module='spring-core', version='7.0.0-M6')
// @Grab(group='org.springframework.experimental', module='mcp', version='0.6.0', initClass=false)
// @Grab(group='org.springframework.experimental', module='mcp-webmvc-sse-transport', version='0.6.0', initClass=false)
// @Grab(group='org.springframework.experimental', module='mcp-webflux-sse-transport', version='0.6.0', initClass=false)
// @Grab(group='org.springframework.ai', module='spring-ai-mcp', version='1.0.0-M7', initClass=false)
// @Grab(group='org.springframework.ai', module='spring-ai-openai', version='1.0.0-M7', initClass=false)
// @Grab(group='org.springframework', module='spring-webmvc', version='6.1.14', initClass=false)
// @Grab(group='org.springframework', module='spring-context', version='6.1.14', initClass=false)
// @Grab(group='jakarta.servlet', module='jakarta.servlet-api', version='5.0.0', initClass=false)
// @Grab(group='com.fasterxml.jackson.core', module='jackson-databind', version='2.17.2', initClass=false)
// @Grab(group='org.slf4j', module='slf4j-api', version='2.0.16', initClass=false)
// @Grab(group='org.slf4j', module='slf4j-simple', version='2.0.16', initClass=false)

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.mcp.client.McpClient

import org.springframework.ai.chat.client.advisor.api.Advisor
import org.springframework.ai.chat.client.advisor.api.AdvisorContext
import org.springframework.ai.chat.client.advisor.api.AdvisorResponse
import org.springframework.ai.chat.client.advisor.api.ChatClientAdvisor


def apiKey = System.getenv("crafter_chatgpt")

def query = params.question

if(!query) {
    return "Error: 'question' field is required"
}

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


 