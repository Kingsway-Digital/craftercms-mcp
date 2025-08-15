package org.craftercms.ai.mcp.server.tools

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
        return "none"
    }
}