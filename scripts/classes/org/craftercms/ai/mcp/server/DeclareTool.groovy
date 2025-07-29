package org.craftercms.ai.mcp.server

import java.lang.annotation.*

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DeclareTool {
    String toolName()
    String toolDescription()
    String returnType()
}