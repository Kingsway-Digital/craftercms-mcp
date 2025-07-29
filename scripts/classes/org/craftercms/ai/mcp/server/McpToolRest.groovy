package org.craftercms.ai.mcp.server

import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient

class McpToolRest extends McpTool {
    def baseUrl
    def url
    def method

     public Object call(args) {

      def siteId = "mcp"
      def mcpServerUrl = "http://localhost:8080/"
      def previewToken = "CCE-V1#5qFpTjXlyPDsrq5FGMCJSA3oDo1DTgK/qYQXFUBSe1zxHpoZFXf30uWCU6eRgefl"
    
      def restClient = RestClient.builder()
        .baseUrl(baseUrl)
        .defaultHeaders { headers ->
            headers.set(HttpHeaders.CONTENT_TYPE, "application/json")
            headers.set(HttpHeaders.ACCEPT, "application/json")
            headers.set("X-Crafter-Site", siteId)
            headers.set("X-Crafter-Preview", previewToken)
        }
        .build()

         def response = 
         restClient.get()
            .uri(url+"?city="+args[0])
            .retrieve()
            .body(String.class)

        response = response.replaceAll("\"","")

        return response
     }  

}