package org.artificers.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Loads CSV reader mappings from JSON files and registers a {@link TransactionCsvReader}
 * bean for each mapping. Dependencies are injected so that the loader can be
 * tested in isolation and to follow constructor-based immutability.
 */
@Component
public class CsvMappingLoader implements BeanDefinitionRegistryPostProcessor {
    private final ObjectMapper mapper;
    private final ResourcePatternResolver resolver;

    public CsvMappingLoader(ObjectMapper mapper, ResourcePatternResolver resolver) {
        this.mapper = mapper;
        this.resolver = resolver;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        try {
            Resource[] resources = resolver.getResources("classpath:mappings/*.json");
            GenericApplicationContext context = (GenericApplicationContext) registry;
            for (Resource r : resources) {
                ConfigurableCsvReader.Mapping mapping =
                        mapper.readValue(r.getInputStream(), ConfigurableCsvReader.Mapping.class);
                ConfigurableCsvReader reader = new ConfigurableCsvReader(mapping);
                context.registerBean(mapping.institution() + "CsvReader", TransactionCsvReader.class, () -> reader);
            }
        } catch (IOException e) {
            throw new BeanDefinitionStoreException("Failed to load CSV mappings", e);
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // no-op
    }
}

