package org.artificers.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Locates mapping definition files on the classpath and converts them to
 * {@link ConfigurableCsvReader.Mapping} objects.
 */
@Component
public class MappingFileLocator {
    private final ObjectMapper mapper;
    private final ResourcePatternResolver resolver;

    public MappingFileLocator(ObjectMapper mapper, ResourcePatternResolver resolver) {
        this.mapper = mapper;
        this.resolver = resolver;
    }

    /**
     * Find all JSON mapping files under {@code classpath:mappings}.
     */
    public List<ConfigurableCsvReader.Mapping> locate() throws IOException {
        Resource[] resources = resolver.getResources("classpath:mappings/*.json");
        List<ConfigurableCsvReader.Mapping> mappings = new ArrayList<>();
        for (Resource r : resources) {
            mappings.add(mapper.readValue(r.getInputStream(), ConfigurableCsvReader.Mapping.class));
        }
        return mappings;
    }
}

