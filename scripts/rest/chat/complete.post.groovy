
@GrabResolver(name='custom', root='https://repo.spring.io/snapshot', m2Compatible='true')
    @Grab(group='org.springframework.ai', module='spring-ai-core',   version='1.0.0-M6', initClass=false)
@Grab(group='org.springframework.ai',     module='spring-ai-openai', version='1.0.0-M6', initClass=false)
//@Grab(group='org.springframework.ai', module='spring-ai-mcp', version='1.1.0-SNAPSHOT', initClass=false)
//@Grab(group='org.springframework.ai', module='spring-ai-mcp-client-spring-boot-starter', version='1.1.0-SNAPSHOT', initClass=false)

import groovy.json.JsonSlurper

import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClient.Builder
import org.springframework.web.reactive.function.client.WebClient

import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
//import org.springframework.ai.mcp.client.McpClient

import org.springframework.ai.openai.api.OpenAiApi

import org.springframework.ai.chat.client.ChatClient
// import org.springframework.ai.chat.client.advisor.api.AdvisorContext
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse
import org.springframework.ai.chat.client.advisor.api.Advisor
import org.springframework.ai.tool.StaticToolCallbackProvider       
import org.springframework.ai.chat.client.DefaultChatClientBuilder

    

def apiKey = System.getenv("crafter_chatgpt")

def jsonSlurper = new JsonSlurper()
def requestBody = request.reader.text  
def query = jsonSlurper.parseText(requestBody).message

if(!query) {
    return "Error: 'question' field is required"
}

def webClientBuilder = WebClient.builder()
def restClientBuilder = RestClient.builder()
restClientBuilder.defaultHeaders { it.set(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate") }

def openAiApi = new OpenAiApi("https://api.openai.com", apiKey, restClientBuilder, webClientBuilder)

def openAiChatOptions = OpenAiChatOptions.builder().model("gpt-4o-mini").build() 

def chatModel = new OpenAiChatModel(openAiApi, openAiChatOptions)

chatClient = new DefaultChatClientBuilder(chatModel).build() 

def clientResponse = chatClient.prompt().user(query).call().content()

return [ response: clientResponse ]


// ( apiKey, openAiChatOptions )

// // Define McpClient
// def sseConnections = [
//     'mcp-server': [
//         url: 'http://localhost:8080'
//     ]
// ]

/*StaticToolCallbackProvider
def mcpClient = new McpClient( toolCallbackEnabled: true, sseConnections: sseConnections )

// Define ToolCallbackProvider
def toolCallbackProvider = new ToolCallbackProvider()

// Define ChatClient
def chatClientBuilder = ChatClient.builder(chatModel)

def chatClient = chatClientBuilder.defaultToolCallbacks([toolCallbackProvider]).build()

def clientResponse = chatClient.prompt().user(query).call().content()
*/



// class MyToolCallbackProvider implements ToolCallbackProvider {

//     AdvisorResponse CallAdvisor(AdvisorContext context, Advisor chain) {
//         def tools = [
//             [
//                 type: 'function',
//                 function: [
//                     name: 'get_weather',
//                     description: 'Get the current weather for a location',
//                     parameters: [
//                         type: 'object',
//                         properties: [
//                             location: [
//                                 type: 'string',
//                                 description: 'The city and state, e.g., New York, NY'
//                             ]
//                         ],
//                         required: ['location']
//                     ]
//                 ]
//             ]
//         ]
//         context.chatClient().prompt().tools(tools)
//         chain.advise(context)
//     }
// }