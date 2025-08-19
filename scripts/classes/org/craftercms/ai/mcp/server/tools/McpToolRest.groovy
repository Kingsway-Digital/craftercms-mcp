package org.craftercms.ai.mcp.server.tools

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.craftercms.engine.service.context.SiteContext

class McpToolRest extends McpTool {

    private static final Logger logger = LoggerFactory.getLogger(McpToolRest.class)

    /**
     * Track the type of param being specified for this REST request - is it
     * a query param, a header param, cookie, or path?
     */
    static enum ParamType {
        query, header, cookie, path
    }

    Map<String, ParamType> paramTypes = new HashMap<>()

    String baseUrl
    String url
    String method = "GET"
    String previewToken
    String siteId

    @Override
    Object call(Map<String, String> args) {
        logger.info("McpToolRest called for: {} {}", method, url)

        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeaders { headers ->
                    headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    headers.set("X-Crafter-Site", siteId)
                    headers.set("X-Crafter-Preview", previewToken)
                }
                .build()

        RestClient.RequestBodyUriSpec spec = restClient.method(HttpMethod.valueOf(method))
        String urlPathQuery = url

        for (String name : args.keySet()) {
            ToolParam param = getParamDescriptor(name)
            if (param == null) {
                throw new IllegalArgumentException("Param " + name + " not expected")
            }
            ParamType type = paramTypes.get(name)
            if (type == null) type = ParamType.query // backward compatibility
            switch (type) {
                case ParamType.header:
                    spec.header(name, args.get(name))
                    break
                    logger.error("Cookie parameter not supported yet. Ignoring requested param  {}", name)
                    break
                case ParamType.path:
                    urlPathQuery.replace("{" + name + "}", args.get(name))
                    break
                case ParamType.query:
                    if (urlPathQuery.contains('?')) urlPathQuery += '&'
                    else urlPathQuery += '?'
//                     urlPathQuery += URLEncoder.encode(name, "UTF-8")
                    urlPathQuery += name
                    urlPathQuery += '='
//                      urlPathQuery += URLEncoder.encode(args.get(name), "UTF-8")
                    urlPathQuery += args.get(name)
                    break
            }
        }
        logger.info("McpToolRest about to call API {} with args {}",urlPathQuery, args)

        return spec.uri(urlPathQuery).retrieve().body(String.class)
    }
}