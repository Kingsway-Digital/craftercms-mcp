package org.craftercms.ai.mcp.server.tools

import java.lang.reflect.Method

class McpToolReflect extends McpTool {
    Object serviceObject
    String methodName

    Object call(Map<String,String> args) {
        Class<?> clazz = serviceObject.getClass()

        def argTypes = []
        params.each { param ->

            switch(param.type) {
                case "string":
                    argTypes.add(String.class)
                    break

                default:
                    argTypes.add(Object.class)
                    break

            }
        }

        def argClasses = (Class[])argTypes.toArray()
        Method method = clazz.getMethod(methodName, argClasses)

        def result = method.invoke(serviceObject, args.values().toArray())
        return result
    }

}