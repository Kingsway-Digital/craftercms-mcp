package org.craftercms.ai.mcp.server.tools

import org.craftercms.ai.mcp.server.auth.CredentialType

abstract class McpTool {
    String toolName
    String toolDescription
    String returnType
    String[] scopes
    List<ToolParam> params = new ArrayList<>()

    static class ToolParam {
        String name
        String type
        String description
        boolean required

        @Override
        String toString() {
            return "ToolParam{" +
                    "name='" + name + '\'' +
                    ", type='" + type + '\'' +
                    ", description='" + description + '\'' +
                    ", required=" + required +
                    '}';
        }
    }

    /**
     * Call the tool
     * @param args name-value pair corresponding to the ToolParam.name and the value of the param
     * @return the call result
     */
    abstract Object call(Map<String, String> args)

    ToolParam getParamDescriptor(String paramName) {
        return params.stream().filter({p -> p.getName().equals(paramName)}).findFirst().orElse(null)
    }

    public String[] getRequiredScopes() {
        return scopes
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