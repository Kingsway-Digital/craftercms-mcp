package org.craftercms.ai.mcp.client

import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.definition.ToolDefinition


public class ClientToolCallback implements ToolCallback {

    def name
    def description
    def inputSchema
    def client 

    String getName() {
        return name
    }

    String getDescription() {
        return description ?: "MCP tool: ${name}"
    }

    ToolDefinition getToolDefinition() {
        // Create a basic tool definition from MCP tool schema
        return ToolDefinition.builder()
            .name(name)
            .description(description ?: "MCP tool: ${name}")
            .inputSchema(inputSchema ?: [:])
            .build()
    }

    String call(String arguments) {

        try {            
            // Parse arguments JSON
            def argMap = [:]
            
            if (arguments && arguments.trim()) {
                argMap = new groovy.json.JsonSlurper().parseText(arguments)
            }

            
            def result = client.callTool(name, argMap)
            def response = result.content ?: result.output ?: result.toString()
            
            return response
            
        } catch (Exception e) {
            return "Error calling tool: ${e.message}"
        }
    }
    
}