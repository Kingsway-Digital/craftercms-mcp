
def server = applicationContext["crafterMcpServer"]

response.setContentType("application/x-ndjson")
response.setCharacterEncoding("UTF-8")
response.setHeader("Transfer-Encoding", "chunked")
response.setHeader("Cache-Control", "no-cache")
response.setHeader("Connection", "keep-alive")

server.doPostStreaming(request, response)   

