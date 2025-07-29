package org.craftercms.ai.mcp.server

import java.lang.annotation.*

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(DeclareToolParams.class)
public @interface DeclareToolParam {
    String name()
    String type() 
    String description()
}
 
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DeclareToolParams {
    DeclareToolParam[] value()
}