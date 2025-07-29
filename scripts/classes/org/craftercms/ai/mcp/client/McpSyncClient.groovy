package org.craftercms.ai.mcp.client

import com.fasterxml.jackson.databind.ObjectMapper

import org.springframework.web.client.RestClient

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * MCP Sync Client that implements synchronous HTTP communication
 */
class McpSyncClient {

    private static final Logger logger = LoggerFactory.getLogger(McpSyncClient.class)

    private final RestClient restClient
    private final ObjectMapper objectMapper
    private boolean initialized = false

    McpSyncClient(restClient) {
        this.restClient = restClient
        this.objectMapper = new ObjectMapper()
    }

    def initialize() {
        def request = [
            jsonrpc: "2.0",
            method: "initialize",
            params: [
                clientInfo: [
                    name: "mcp-client",
                    version: "1.0.0"
                ],
                clientCapabilities: [
                    roots: true,
                    sampling: true
                ]
            ],
            id: UUID.randomUUID().toString()
        ]

        logger.info("Sending initialize request: ${objectMapper.writeValueAsString(request)}")
        
        def response = restClient.post()
            .uri("/api/craftermcp/mcp.json")
            .body(request)
            .retrieve()
            .toEntity(Map.class)

        logger.info("Received initialize response: ${objectMapper.writeValueAsString(response.body)}")
        
        if (response.body.error) {
            throw new RuntimeException("Initialize failed: ${response.body.error.message}")
        }
        
        initialized = true
        return response.body.result
    }

    def listTools() {
        if (!initialized) {
            throw new IllegalStateException("Client not initialized")
        }

        def request = [
            jsonrpc: "2.0",
            method: "tools/list",
            params: [:],
            id: UUID.randomUUID().toString()
        ]

        logger.info("Sending listTools request: ${objectMapper.writeValueAsString(request)}")
        
        def response = restClient.post()
            .uri("/api/craftermcp/mcp.json")
            .body(request)
            .retrieve()

        def responseObj = response.toEntity(Map.class)

        return responseObj.body.result
    }

    def callTool(String toolName, Map parameters) {

        if (!initialized) {
            throw new IllegalStateException("Client not initialized")
        }

        def request = [
            jsonrpc: "2.0",
            method: "tools/call",
            params: [
                name: toolName,
                arguments: parameters
            ],
            id: UUID.randomUUID().toString()
        ]

        logger.info("Sending callTool request: ${objectMapper.writeValueAsString(request)}")
        
        def response = restClient.post()
            .uri("/api/craftermcp/mcp.json")
            .body(request)
            .retrieve()
            .toEntity(Map.class)

        logger.info("Received callTool response: ${objectMapper.writeValueAsString(response.body)}")
        
        if (response.body.error) {
            throw new RuntimeException("Tool call failed: ${response.body.error.message}")
        }
        
        return response.body.result
    }

    boolean isInitialized() {
        return initialized
    }
}