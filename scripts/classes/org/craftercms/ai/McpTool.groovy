package org.craftercms.ai

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
}