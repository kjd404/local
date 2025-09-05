package org.artificers.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Locates mapping definition files on the classpath and converts them to
 * {@link ConfigurableCsvReader.Mapping} objects.
 */
public class MappingFileLocator {
    private final ObjectMapper mapper;

    public MappingFileLocator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Find all JSON mapping files under {@code classpath:mappings}.
     */
    public List<ConfigurableCsvReader.Mapping> locate() throws IOException {
        List<ConfigurableCsvReader.Mapping> mappings = new ArrayList<>();
        URL url = getClass().getClassLoader().getResource("mappings");
        if (url == null) {
            return mappings;
        }
        try {
            Path dir = Paths.get(url.toURI());
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
                for (Path p : stream) {
                    try (InputStream in = Files.newInputStream(p)) {
                        mappings.add(mapper.readValue(in, ConfigurableCsvReader.Mapping.class));
                    }
                }
            }
        } catch (URISyntaxException e) {
            throw new IOException("Invalid mappings path", e);
        }
        return mappings;
    }
}

