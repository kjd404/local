package org.artificers.ingest.csv;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Locates mapping definition files on the classpath and converts them to {@link
 * ConfigurableCsvReader.Mapping} objects.
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
    if (baseDir != null) {
      readMappings(baseDir, mappings);
      return mappings;
    }

    URL url = getClass().getClassLoader().getResource("mappings");
    if (url == null) {
      return mappings;
    }

    try {
      URI uri = url.toURI();
      if ("jar".equals(uri.getScheme())) {
        try (FileSystem fs = FileSystems.newFileSystem(uri, Map.of())) {
          readMappings(fs.getPath("mappings"), mappings);
        }
      } else {
        readMappings(Paths.get(uri), mappings);
      }
    } catch (URISyntaxException e) {
      throw new IOException("Invalid mappings path", e);
    }
    return mappings;
  }

  private void readMappings(Path dir, List<ConfigurableCsvReader.Mapping> mappings)
      throws IOException {
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
      for (Path p : stream) {
        try (InputStream in = Files.newInputStream(p)) {
          mappings.add(mapper.readValue(in, ConfigurableCsvReader.Mapping.class));
        }
      }
    }
  }
}
