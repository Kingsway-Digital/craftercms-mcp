package org.craftercms.ai.mcp.server.tools

import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient
import org.craftercms.engine.service.context.SiteContext
import org.craftercms.ai.mcp.server.tools.McpTool.ToolParam

class McpToolRest extends McpTool {
    def baseUrl
    def url
    def method
    def siteId
    def previewToken

    public Object call(args) {
        siteId = SiteContext.getCurrent().getSiteName()

        def restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeaders { headers ->
                    headers.set(HttpHeaders.CONTENT_TYPE, "application/json")
                    headers.set(HttpHeaders.ACCEPT, "application/json")
                    headers.set("X-Crafter-Site", siteId)
                    headers.set("X-Crafter-Preview", previewToken)
                }
                .build()

        String urlParams = ""
        for (int i = 0; i < args.size(); i++) {
            ToolParam p = this.params.get(i)
            String val = (String) args[0]
            if (urlParams.length() > 0) urlParams += '&'
            urlParams += URLEncoder.encode(p.name, "UTF-8")
            urlParams += '='
            urlParams += URLEncoder.encode(val, "UTF-8")
        }


        def response =
                restClient.get()
                        .uri(url + "?" + urlParams)
                        .retrieve()
                        .body(String.class)

        response = response.replaceAll("\"", "")

        return response
    }

}