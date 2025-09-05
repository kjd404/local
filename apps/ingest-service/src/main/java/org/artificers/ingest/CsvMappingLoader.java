package org.artificers.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Loads CSV mapping files and registers {@link ConfigurableCsvReader} beans
 * before the application context is fully refreshed so that they are available
 * for constructor injection into {@link IngestService}.
 */
@Component
public class CsvMappingLoader implements BeanDefinitionRegistryPostProcessor {

    private final ObjectMapper mapper;
    private final ApplicationContext context;

    public CsvMappingLoader(ObjectMapper mapper, ApplicationContext context) {
        this.mapper = mapper;
        this.context = context;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        try {
            Resource[] resources = context.getResources("classpath:mappings/*.json");
            for (Resource r : resources) {
                ConfigurableCsvReader.Mapping mapping =
                        mapper.readValue(r.getInputStream(), ConfigurableCsvReader.Mapping.class);
                GenericBeanDefinition bd = new GenericBeanDefinition();
                bd.setBeanClass(ConfigurableCsvReader.class);
                bd.getConstructorArgumentValues().addGenericArgumentValue(mapping);
                registry.registerBeanDefinition(mapping.institution() + "CsvReader", bd);
            }
        } catch (IOException e) {
            throw new BeansException("Failed to load CSV mappings", e) {};
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // no-op
    }
}
