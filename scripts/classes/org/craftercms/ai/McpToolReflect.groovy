package org.craftercms.ai

import java.lang.reflect.Method

class McpToolReflect extends McpTool {
    def serviceObject
    def methodName

     public Object call(args) {

        Class<?> clazz = serviceObject.getClass()
        Method method = clazz.getMethod(methodName, String.class)
        def result = method.invoke(person, args)

        return result
     }  

}