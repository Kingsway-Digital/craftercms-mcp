package org.craftercms.ai.mcp.server.resources;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class McpResourceTemplate {

    private static final Logger logger = LoggerFactory.getLogger(McpResourceTemplate.class);

    String name
    String getName() { return name }
    void setName(String value) { name = value }

    String description
    String getDescription() { return description }
    void setDescription(String value) { description = value }

    String uriTemplate
    String getUriTemplate() { return uriTemplate }
    void setUriTemplate(String value) { uriTemplate = value }

}