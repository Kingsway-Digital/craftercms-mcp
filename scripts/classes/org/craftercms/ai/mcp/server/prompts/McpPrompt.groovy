package org.craftercms.ai.mcp.server.prompts;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class McpPrompt {

    private static final Logger logger = LoggerFactory.getLogger(McpPrompt.class);

    String name
    String getName() { return name }
    void setName(String value) { name = value }

    String description
    String getDescription() { return description }
    void setDescription(String value) { description = value }

    String promptTemplate
    String getPromptTemplate() { return promptTemplate }
    void setPromptTemplate(String value) { promptTemplate = value }

}