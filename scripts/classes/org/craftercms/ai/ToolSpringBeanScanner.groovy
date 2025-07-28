package org.craftercms.ai


import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.stereotype.Component


class ToolSpringBeanScanner {

    private static final Logger logger = LoggerFactory.getLogger(ToolSpringBeanScanner.class)

    @Autowired
    private ApplicationContext applicationContext

    public ToolSpringBeanScanner() { 

    }  

    public void scan() {
        String[] beanNames = []  //applicationContext.getBeanDefinitionNames()

        for (String name : beanNames) {
            Object bean = applicationContext.getBean(name)
            Class<?> beanClass = bean.getClass()

            DeclareTool declareToolAnnotation = AnnotationUtils.findAnnotation(beanClass, DeclareTool.class)

            if (DeclareTool != null) {
                
                System.out.println("Found tool bean: " + name + " (class: " + beanClass.getSimpleName() + ")");
            }
        }        
    }
}
