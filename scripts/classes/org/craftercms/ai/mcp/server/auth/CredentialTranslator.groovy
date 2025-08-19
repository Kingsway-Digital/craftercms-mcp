package org.craftercms.ai.mcp.server.auth

import org.craftercms.ai.mcp.server.tools.*

class CredentialTranslator {
    String translateCredentials(String userId, String[] scopes, McpTool tool) {
        switch (tool.getAuthType()) {
            case CredentialType.NONE:
                return "";

            case CredentialType.API_KEY:
                String apiKey = tool.getAuthConfig().get("apiKey");
                return apiKey != null ? apiKey : "default-api-key";

            case CredentialType.BASIC_AUTH:
                String username = userId;
                String password = tool.getAuthConfig().get("password");
                String credentials = username + ":" + password;
                return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());

            case CredentialType.CUSTOM_HEADER:
                String headerName = tool.getAuthConfig().get("headerName");
                String headerValue = tool.getAuthConfig().get("headerValue");
                return headerValue != null ? headerValue : userId;

            default:
                logger.warn("Unsupported auth type for tool {}: {}", tool.getToolName(), tool.getAuthType());
                return null;
        }
    }
}
