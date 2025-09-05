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
    private final Path baseDir;

    public MappingFileLocator(ObjectMapper mapper) {
        this(mapper, null);
    }

    public MappingFileLocator(ObjectMapper mapper, Path baseDir) {
        this.mapper = mapper;
        this.baseDir = baseDir;
    }

    /**
     * Find all JSON mapping files either under the provided directory or {@code classpath:mappings}.
     */
    public List<ConfigurableCsvReader.Mapping> locate() throws IOException {
        List<ConfigurableCsvReader.Mapping> mappings = new ArrayList<>();
        Path dir = baseDir;
        if (dir == null) {
            URL url = getClass().getClassLoader().getResource("mappings");
            if (url == null) {
                return mappings;
            }
            try {
                dir = Paths.get(url.toURI());
            } catch (URISyntaxException e) {
                throw new IOException("Invalid mappings path", e);
            }
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path p : stream) {
                try (InputStream in = Files.newInputStream(p)) {
                    mappings.add(mapper.readValue(in, ConfigurableCsvReader.Mapping.class));
                }
            }
        }
        return mappings;
    }
}

