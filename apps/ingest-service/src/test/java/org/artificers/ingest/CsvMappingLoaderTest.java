package org.artificers.ingest;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CsvMappingLoaderTest {
    @Test
    void registersBeansForEachMapping() throws Exception {
        ConfigurableCsvReader.Mapping m1 = new ConfigurableCsvReader.Mapping("x1", Map.of());
        ConfigurableCsvReader.Mapping m2 = new ConfigurableCsvReader.Mapping("x2", Map.of());
        MappingFileLocator locator = new MappingFileLocator(null, null) {
            @Override
            public List<ConfigurableCsvReader.Mapping> locate() {
                return List.of(m1, m2);
            }
        };
        CsvMappingLoader loader = new CsvMappingLoader(locator);
        GenericApplicationContext context = new GenericApplicationContext();
        loader.postProcessBeanDefinitionRegistry(context);
        context.refresh();

        TransactionCsvReader r1 = context.getBean("x1CsvReader", TransactionCsvReader.class);
        TransactionCsvReader r2 = context.getBean("x2CsvReader", TransactionCsvReader.class);
        assertEquals("x1", r1.institution());
        assertEquals("x2", r2.institution());
        context.close();
    }
}

