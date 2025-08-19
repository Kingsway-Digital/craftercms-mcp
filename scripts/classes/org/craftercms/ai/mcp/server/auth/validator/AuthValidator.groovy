package org.craftercms.ai.mcp.server.auth.validator

import jakarta.servlet.http.HttpServletResponse;

import org.craftercms.ai.mcp.server.tools.*;

interface AuthValidator {

    public String[] validate(String authHeader, HttpServletResponse resp) throws IOException;
}