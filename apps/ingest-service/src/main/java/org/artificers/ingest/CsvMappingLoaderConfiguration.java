package org.artificers.ingest;

import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link CsvMappingLoader} with constructor-based dependency injection.
 */
@Configuration
class CsvMappingLoaderConfiguration {
    @Bean
    static BeanDefinitionRegistryPostProcessor csvMappingLoader(MappingFileLocator locator) {
        return new CsvMappingLoader(locator);
    }
}
