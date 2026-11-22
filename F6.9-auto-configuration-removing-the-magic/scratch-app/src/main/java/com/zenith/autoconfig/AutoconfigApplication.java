package com.zenith.autoconfig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class AutoconfigApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context =
                SpringApplication.run(AutoconfigApplication.class, args);

        // Bean census: how many beans exist, and who declared the ones we never wrote?
        System.out.println("--- bean census ---");
        System.out.println("total beans in the context : " + context.getBeanDefinitionCount());
        System.out.println("dispatcherServlet exists   : " + context.containsBean("dispatcherServlet"));
        System.out.println("jacksonPresentNote exists  : " + context.containsBean("jacksonPresentNote"));
        System.out.println("postgresPresentNote exists : " + context.containsBean("postgresPresentNote"));
        System.out.println("dataSource exists          : " + context.containsBean("dataSource"));
        System.out.println("entityManagerFactory exists: " + context.containsBean("entityManagerFactory"));
        System.out.println("transactionManager exists  : " + context.containsBean("transactionManager"));
        System.out.println("myObjectMapper exists      : " + context.containsBean("myObjectMapper"));
        if (context.containsBean("jacksonObjectMapper")) {
            System.out.println("jacksonObjectMapper        : present, declared by "
                    + context.getBeanFactory().getBeanDefinition("jacksonObjectMapper").getFactoryBeanName());
        } else {
            System.out.println("jacksonObjectMapper        : not present (Boot backed off; another ObjectMapper bean exists)");
        }
    }
}
