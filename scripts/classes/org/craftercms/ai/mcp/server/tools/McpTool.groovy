package org.craftercms.ai.mcp.server.tools

import org.craftercms.ai.mcp.server.auth.CredentialType

abstract class McpTool {
    def toolName
    def toolDescription 
    def returnType
    def params

    static public class ToolParam {
        def name
        def type
        def description
        def required
    }

    public abstract Object call(args) 

    public String[] getRequiredScopes() {
        return new String[0];
    }  

    public String getAuthType() {
        // For the moment the server will only handle NONE 
        // What this means is that the server asssumes that you must authenticate against it BUT once you do, tools are authenticated.
        // This means that tools are using pre-configured SERVICE authentications
        //
        // Once this is complete we'll circle back to allowing tools to declare that they need to delegate authentication to the user
        // Scenario: I am an MCP server, I offer a tool to work with Box or Crafter Studio or whatever -- but YOU the user need to sign in, and I want the MCP server to facilitate that.
        return CredentialType.NONE;
    }
}