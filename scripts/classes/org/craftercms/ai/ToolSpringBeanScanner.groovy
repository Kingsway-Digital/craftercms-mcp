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
        mcpTool.returnType = declareToolAnnotation.returnType()
        
        mcpTool.params = []

        DeclareToolParam[] toolParamAnnotations = method.getAnnotationsByType(DeclareToolParam.class);

        toolParamAnnotations.each { paramAnnotation ->
            logger.info("processing param '${paramAnnotation.name()}' ")

            def param = new org.craftercms.ai.McpTool.ToolParam()
            param.name = paramAnnotation.name()
            param.type = paramAnnotation.type()
            param.description = paramAnnotation.description()
            param.required = true

            mcpTool.params.add(param)
        }

        /* add the tool to the server */
        mcpServer.mcpTools.add(mcpTool)
    }
}
