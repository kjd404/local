package org.artificers.ingest;

import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * Registers {@link CsvMappingLoader} with constructor-based dependency injection.
 */
@Configuration
class CsvMappingLoaderConfiguration {
    @Bean
    ResourcePatternResolver resourcePatternResolver(ResourceLoader loader) {
        return new PathMatchingResourcePatternResolver(loader);
    }

    @Bean
    static BeanDefinitionRegistryPostProcessor csvMappingLoader(MappingFileLocator locator) {
        return new CsvMappingLoader(locator);
    }
}
