package org.artificers.ingest;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.context.support.GenericApplicationContext;

import java.io.IOException;
import java.util.List;

/**
 * BeanDefinitionRegistryPostProcessor that loads CSV reader mappings from JSON
 * files and registers a {@link TransactionCsvReader} bean for each mapping. It
 * is registered via a configuration class to enable constructor-based
 * dependency injection even though BeanDefinitionRegistryPostProcessor
 * instances are created very early in the Spring lifecycle.
 */
public class CsvMappingLoader implements BeanDefinitionRegistryPostProcessor {
    private final MappingFileLocator locator;

    public CsvMappingLoader(MappingFileLocator locator) {
        this.locator = locator;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        try {
            List<ConfigurableCsvReader.Mapping> mappings = locator.locate();
            GenericApplicationContext context = (GenericApplicationContext) registry;
            for (ConfigurableCsvReader.Mapping mapping : mappings) {
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

