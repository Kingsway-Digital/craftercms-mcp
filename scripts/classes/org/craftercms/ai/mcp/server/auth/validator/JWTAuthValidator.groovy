package org.craftercms.ai.mcp.server.auth.validator

import jakarta.servlet.http.HttpServletResponse;

import org.craftercms.ai.mcp.server.tools.*

class JWTAuthValidator implements AuthValidator {

    public String[] validate(String authHeader, HttpServletResponse resp) throws IOException {

     if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.warn("No valid Authorization header received");
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setHeader("WWW-Authenticate", "Bearer realm=\"example\", error=\"missing_token\", " +
                "error_description=\"Authorization header missing or invalid\", " +
                "authorization_uri=\"https://auth.example.com/oauth/authorize\", " +
                "discovery_uri=\"https://auth.example.com/.well-known/oauth-authorization-server\"");
            return null;
        }

        String token = authHeader.substring(7);
        try {
            String jwksUri = "https://auth.example.com/.well-known/jwks.json";
            // Fetch JWKS using HttpClient
            HttpClient client = HttpClient.newBuilder().build();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(jwksUri))
                .GET()
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Failed to fetch JWKS: HTTP " + response.statusCode());
            }
            String jwksJson = response.body();

            // Parse JWT to get the kid
            JwsHeader header = Jwts.parserBuilder()
                .build()
                .parse(token)
                .getHeader();
            String kid = header.getKeyId();
            if (kid == null) {
                throw new JwtException("Missing key ID in JWT header");
            }

            // Parse JWKS and find the matching JWK
            JsonObject jwks = gson.fromJson(jwksJson, JsonObject.class);
            JsonArray keys = jwks.getAsJsonArray("keys");
            Jwk<?> jwk = null;
            for (JsonElement key : keys) {
                Jwk<?> candidate = Jwks.parser().build().parse(key.toString());
                if (kid.equals(candidate.getId())) {
                    jwk = candidate;
                    break;
                }
            }
            if (jwk == null) {
                throw new JwtException("No JWK found for kid: " + kid);
            }

            // Validate JWT
            Jws<Claims> claims = Jwts.parserBuilder()
                .setSigningKey(jwk.getKey())
                .build()
                .parseClaimsJws(token);

            if (!claims.getBody().getAudience().contains("https://api.example.com/mcp")) {
                logger.warn("Invalid token audience");
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                resp.setHeader("WWW-Authenticate", "Bearer realm=\"example\", error=\"invalid_token\", " +
                    "error_description=\"Invalid audience\"");
                return null;
            }
            if (claims.getBody().getExpiration().before(new java.util.Date())) {
                logger.warn("Token expired");
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                resp.setHeader("WWW-Authenticate", "Bearer realm=\"example\", error=\"invalid_token\", " +
                    "error_description=\"Token expired\"");
                return null;
            }

            String userId = claims.getBody().getSubject();
            String scopes = claims.getBody().get("scope", String.class);
            return new String[]{userId, scopes};
        } catch (JwtException | IllegalArgumentException e) {
            logger.error("Token validation failed: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setHeader("WWW-Authenticate", "Bearer realm=\"example\", error=\"invalid_token\", " +
                "error_description=\"Token validation failed\"");
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching JWKS: {}", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return null;
        }        
    }
}
