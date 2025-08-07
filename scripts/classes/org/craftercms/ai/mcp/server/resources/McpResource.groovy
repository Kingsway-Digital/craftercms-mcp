package org.craftercms.ai.mcp.server.resources;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class McpResource {

    private static final Logger logger = LoggerFactory.getLogger(McpResource.class);

    String name
    String getName() { return name }
    void setName(String value) { name = value }

    String description
    String getDescription() { return description }
    void setDescription(String value) { description = value }

    String uri
    String getUri() { return uri }
    void setUri(String value) { uri = value }

}