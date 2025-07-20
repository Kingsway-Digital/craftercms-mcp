import org.craftercms.ai.CrafterMcpServer 

System.out.println("Nessage MCP AI call")
System.out.println(params)
System.out.println(request.reader.text)

// Sample inbound message
// {"jsonrpc":"2.0","method":"initialize","id":"f737d2bd-0","params":
//       {"protocolVersion":"2024-11-05",
//        "capabilities":
//            {"roots":{"listChanged":true},"sampling":{}},
//             "clientInfo":{"name":"mcp-client","version":"1.0.0"}}}

def server = new CrafterMcpServer()

server.doPost(request, response)   