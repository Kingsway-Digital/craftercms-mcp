import groovy.json.JsonSlurper

def SAMPLE_TOOL_SCHEMA = "{ "+
   " \"name\": \"query_database\",   "+
   " \"title\": \"Database Query Tool\",   "+
   " \"description\": \"Queries a database for records matching the given criteria\",   "+
   " \"inputSchema\": {   "+
   "   \"type\": \"object\",   "+
   "   \"properties\": {   "+
   "   \"query\": {   "+
   "      \"type\": \"string\",   "+
   "      \"description\": \"SQL query string to execute\"   "+
   "    },   "+
   "    \"limit\": {   "+
   "      \"type\": \"integer\",   "+
   "      \"description\": \"Maximum number of records to return\",   "+
   "      \"minimum\": 1,   "+
   "      \"maximum\": 100   "+
   "    }   "+
   "  },   "+
   "  \"required\": [\"query\"]   "+
   "},   "+
   "\"outputSchema\": {   "+
   "  \"type\": \"object\",   "+
   "  \"properties\": {   "+
   "    \"result\": {   "+
   "      \"type\": \"array\",   "+
   "      \"items\": {   "+
   "        \"type\": \"object\",   "+
   "        \"properties\": {   "+
   "          \"id\": {   "+
   "            \"type\": \"string\",   "+
   "            \"description\": \"Record ID\"   "+
   "          },   "+
   "          \"data\": {   "+
   "            \"type\": \"object\",   "+
   "            \"description\": \"Record data\"   "+
   "          }   "+
   "        },   "+
   "        \"required\": [\"id\"]   "+
   "      },   "+
   "      \"description\": \"List of matching records\"   "+
   "    },   "+
   "    \"isError\": {   "+
   "      \"type\": \"boolean\",   "+
   "      \"description\": \"Indicates if the query resulted in an error\"   "+
   "    },   "+
   "    \"errorMessage\": {   "+
   "      \"type\": \"string\",   "+
   "      \"description\": \"Error message if isError is true\"   "+
   "    }   "+   
   "  },   "+
   "  \"required\": [\"result\", \"isError\"]   "+
   "}   "+
   "}"
   
def jsonSlurper = new JsonSlurper()
def tools = jsonSlurper.parseText(SAMPLE_TOOL_SCHEMA)

return tools
   