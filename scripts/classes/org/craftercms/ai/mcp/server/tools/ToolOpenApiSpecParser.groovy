package org.craftercms.ai.mcp.server.tools

@Grab('io.swagger.parser.v3:swagger-parser:2.1.30')

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.OpenAPIV3Parser
import org.craftercms.ai.mcp.server.CrafterMcpServer
import org.slf4j.Logger
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

class ToolOpenApiSpecParser {
    private static final Logger logger = LoggerFactory.getLogger(ToolOpenApiSpecParser.class);
    private String baseUrl
    private String openApiSpecUrl
    private CrafterMcpServer mcpServer
    private List<String> includeTags
    private List<String> excludeTags

    ToolOpenApiSpecParser() {}

    void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl
    }

    void setOpenApiSpecUrl(String openApiSpecUrl) {
        this.openApiSpecUrl = openApiSpecUrl
    }

    void setMcpServer(CrafterMcpServer mcpServer) {
        this.mcpServer = mcpServer
    }

    /**
     * Set the list of tags to include. If an operation is tagged with anything in this list,
     * it will be converted into a tool. If the list is empty, all tags will be included.
     * @see #setExcludeTags
     * @param includeTags a string list of operation tags to include
     */
    void setIncludeTags(List<String> includeTags) {
        this.includeTags = includeTags
    }

    /**
     * Set the list of tags to exclude. If this list is empty, no otherwise included operation
     * will be excluded. If this list contains values, any operation that contains an excluded
     * tag will be excluded, even if the operation was included via the includeTags list
     * (or lack of one).
     * @see #setIncludeTags
     * @param excludeTags a string list of operation tags to exclude
     */
    void setExcludeTags(List<String> excludeTags) {
        this.excludeTags = excludeTags
    }

    InputStream loadOpenApiSpec() {
        return URI.create(openApiSpecUrl).toURL().openStream()
    }

    void parse() {

        List<McpTool> tools = new ArrayList<>()

        try (InputStream inputStream = loadOpenApiSpec()) {
            if (inputStream == null) {
                throw new IllegalArgumentException("OpenAPI spec not found at specified location")
            }

            String spec = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
            OpenAPI openAPI = new OpenAPIV3Parser().readContents(spec, null, null).getOpenAPI()
            if (openAPI == null) {
                throw new RuntimeException("Failed to parse loaded OpenAPI spec: " + spec)
            }

            String title = openAPI.getInfo().getTitle()
            String description = openAPI.getInfo().getDescription()
            String version = openAPI.getInfo().getVersion()
            String exDocs = openAPI.getExternalDocs().getDescription()
            String exDocUrl = openAPI.getExternalDocs().getUrl()

            openAPI.getPaths().forEach((path, pathItem) -> {
                pathItem.readOperationsMap().forEach((httpMethod, operation) -> {
                    String operationId = operation.getOperationId()
                    if (operationId == null || operationId.isBlank()) return

                    if (includeTags.size() > 0) {
                        long intersectCount = includeTags.stream().distinct().filter(operation.tags::contains).count()
                        if (intersectCount == 0) {
                            return
                        }
                    }
                    if (excludeTags.size() > 0) {
                        long intersectCount = excludeTags.stream().distinct().filter(operation.tags::contains).count()
                        if (intersectCount > 0) {
                            return
                        }
                    }

                    List<Parameter> parameters = operation.getParameters() != null ? operation.getParameters() : Collections.emptyList()

                    McpToolRest tool = new McpToolRest()
                    tool.toolName = operationId
                    tool.toolDescription = String.format("%s -- %s. This tool is a part of %s version %s (%s). More details can be found in %s at %s",
                            operation.getSummary(),
                            operation.getDescription(),
                            title, version, description, exDocs, exDocUrl)
                    tool.returnType = Map.class.getName()
                    tool.params = new ArrayList<>()
                    tool.baseUrl = this.baseUrl
                    tool.url = path
                    tool.method = httpMethod

                    for (Parameter p : parameters) {
                        McpTool.ToolParam tp = new McpTool.ToolParam()
                        tool.params.add(tp)
                        tool.paramTypes.put(p.getName(), McpToolRest.ParamType.valueOf(p.getIn()))
                        tp.setName(p.getName())
                        Schema pSch = p.getSchema()
                        tp.setType(pSch.getType())
                        if (p.getExamples() == null) {
                            tp.setDescription(p.getDescription())
                        } else {
                            StringBuilder b = new StringBuilder(p.getDescription())
                            for (String k : p.getExamples()) {
                                Example e = p.getExamples().get(k)
                                b.append(" "+k)
                                b.append('\n').append(e.getDescription())
                                b.append('\n').append(e.getSummary())
                                b.append('\n').append(e.getValue())
                            }
                            tp.setDescription(b.toString())
                        }
                        tp.setRequired(p.getRequired().booleanValue())
                    }

                    tools.add(tool)
                    logger.debug("OpenAPI converted to tool:\n{}", new groovy.json.JsonBuilder(tool).toPrettyString())
                })
            })
        } catch (Exception e) {
            throw new RuntimeException("Failed to load and parse OpenAPI spec", e);
        }
        mcpServer.mcpTools.addAll(tools)
    }
}
