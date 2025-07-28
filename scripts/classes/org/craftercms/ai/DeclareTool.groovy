package org.craftercms.ai

import java.lang.annotation.*

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DeclareTool {
    String toolName() 
    String toolDescription()
}