package org.craftercms.ai.mcp.server.auth.validator

import jakarta.servlet.http.HttpServletResponse;

import org.craftercms.ai.mcp.server.tools.*

class SimpleAuthValidator implements AuthValidator {

    public String[] scopes; 
    public String[] getScopes() { return scopes; }
    public void setScopes(String[] value) { scopes = value }

    public String[] headerValue; 
    public String[] getScopes() { return scopes; }
    public void setScopes(String[] value) { scopes = value }

    public String[] validate(String authHeader, HttpServletResponse resp) throws IOException {

        if (authHeader == null) {
            logger.warn("No valid Authorization header received");
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return null;
        }
        else {
            if(!authHeader.equals(headerValue)) {
                logger.warn("Invalid authorization header value");
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return null;
            }
            else {
               return scopes
            } 
        }
    }
}
