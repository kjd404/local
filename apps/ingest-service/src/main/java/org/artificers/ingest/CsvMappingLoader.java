package org.artificers.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CsvMappingLoader implements ApplicationContextAware {
    private GenericApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = (GenericApplicationContext) applicationContext;
    }

    @org.springframework.context.event.EventListener(org.springframework.context.event.ContextRefreshedEvent.class)
    public void load() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:mappings/*.json");
        for (Resource r : resources) {
            ConfigurableCsvReader.Mapping mapping = mapper.readValue(r.getInputStream(), ConfigurableCsvReader.Mapping.class);
            ConfigurableCsvReader reader = new ConfigurableCsvReader(mapping);
            context.registerBean(mapping.institution() + "CsvReader", TransactionCsvReader.class, () -> reader);
        }
    }
}
