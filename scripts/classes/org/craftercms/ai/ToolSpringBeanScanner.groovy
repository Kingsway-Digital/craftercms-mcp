package org.craftercms.ai


import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.stereotype.Component

import org.springframework.context.ApplicationContextAware
import org.springframework.beans.BeansException

import java.lang.reflect.Method

class ToolSpringBeanScanner implements ApplicationContextAware {

    private static final Logger logger = LoggerFactory.getLogger(ToolSpringBeanScanner.class)

    private ApplicationContext applicationContext

    public CrafterMcpServer mcpServer
    public CrafterMcpServer getMcpServer() { return mcpServer }
    public void setMcpServer(CrafterMcpServer server) { mcpServer = server }

    public ToolSpringBeanScanner() { 

    }  

    public void setApplicationContext(ApplicationContext context) 
    throws BeansException {
        applicationContext = context
    }

    public void scan() {

        logger.info("Scanning for MCP tools")
        String[] beanNames = applicationContext.getBeanDefinitionNames()

        for (String beanName : beanNames) {
            Object bean = applicationContext.getBean(beanName)
            Class<?> beanClass = bean.getClass()

            for (Method method : beanClass.getDeclaredMethods()) {
                DeclareTool declareToolAnnotation = AnnotationUtils.findAnnotation(method, DeclareTool.class)

                if (declareToolAnnotation != null) {
                    registerTool(beanName, bean, method, declareToolAnnotation)
                }
            }

        }        
    }

    /**
     * Dynamically wires the following spring config
     *   <bean name="toolIsIngredientAvail" class="org.craftercms.ai.McpToolReflect">
     *      <property name="serviceObject" ref="recipeService" />
     *      <property name="methodName" value="isIngredientAvailable" />
     *      <property name="toolName" value="isIngredientAvailable" />
     *      <property name="toolDescription" value="returns a response indicating if an ingredient is available or not" />
     *      <property name="returnType" value="string" />
     *      <property name="params">
     *         <list>
     *           <bean name="param1" class="org.craftercms.ai.McpTool.ToolParam">
     *             <property name="name" value="ingrdient" />
     *             <property name="type" value="string" />
     *             <property name="description" value="The name of the ingredient to check" />
     *             <property name="required" value="true" />
     *           </bean>
     *         </list>
     *    </property>
     *  </bean>
     */
    boolean registerTool(beanName, bean, method, declareToolAnnotation) {
        logger.info("Register MCP tool for for bean `${beanName}` method ${method.getName()} ")
        
        /* build the tool declaration */
        def mcpTool = new McpToolReflect()
        mcpTool.serviceObject = bean
        mcpTool.methodName = method.getName()
        mcpTool.toolName =  declareToolAnnotation.toolName()
        mcpTool.toolDescription = declareToolAnnotation.toolDescription()
        mcpTool.returnType = "string"
        
        mcpTool.params = []
        def param1 = new org.craftercms.ai.McpTool.ToolParam()
        param1.name = "flight"
        param1.type = "string"
        param1.description = "flight"
        param1.required = true

        def param2 = new org.craftercms.ai.McpTool.ToolParam()
        param2.name = "seat"
        param2.type = "string"
        param2.description = "seat"
        param2.required = true

        mcpTool.params.add(param1)
        mcpTool.params.add(param2)

        /* add the tool to the server */
        mcpServer.mcpTools.add(mcpTool)
    }
}
