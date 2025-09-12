package org.artificers.ingest.app;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import org.artificers.ingest.config.DbConfig;
import org.artificers.ingest.config.IngestConfig;
import org.artificers.ingest.di.DaggerIngestComponent;
import org.artificers.ingest.di.IngestComponent;
import org.artificers.ingest.service.AccountShorthandParser;
import org.artificers.ingest.service.DirectoryWatchService;
import org.artificers.ingest.service.FileIngestionService;
import org.artificers.ingest.service.IngestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Application entry point using Dagger and Picocli. */
@Command(name = "ingest", mixinStandardHelpOptions = true)
public final class IngestApp implements Callable<Integer> {
  private static final Logger log = LoggerFactory.getLogger(IngestApp.class);

  @Option(names = "--file", description = "Path to a CSV file")
  Path file;

  @Option(
      names = "--mode",
      description = "Execution mode",
      type = ExecutionMode.class,
      defaultValue = "WATCH")
  ExecutionMode mode;

  @Option(names = "--input", description = "Directory to scan")
  Path input;

  private final IngestService service;
  private final FileIngestionService fileService;
  private final DirectoryWatchService watchService;
  private final IngestConfig config;
  private final AccountShorthandParser shorthandParser;

  public IngestApp(
      IngestService service,
      FileIngestionService fileService,
      DirectoryWatchService watchService,
      IngestConfig config,
      AccountShorthandParser shorthandParser) {
    this.service = service;
    this.fileService = fileService;
    this.watchService = watchService;
    this.config = config;
    this.shorthandParser = shorthandParser;
  }

  @Override
  public Integer call() throws Exception {
    if (file != null) {
      String shorthand = shorthandParser.extract(file);
      if (shorthand != null) {
        try {
          service.ingestFile(file, shorthand);
        } catch (Exception e) {
          log.warn("Ingestion failed for {}", file, e);
        }
      } else {
        log.warn("Skipping file {} with unrecognized name", file);
      }
      return 0;
    }
    if (mode == ExecutionMode.SCAN) {
      Path dir = input != null ? input : config.ingestDir();
      fileService.scanAndIngest(dir);
      return 0;
    }
    try (DirectoryWatchService watch = watchService) {
      watch.start();
      Thread.currentThread().join();
    }
    return 0;
  }

  public static void main(String[] args) throws Exception {
    String rawUrl = System.getenv("DB_URL");
    String user = System.getenv("DB_USER");
    String password = System.getenv("DB_PASSWORD");
    Path ingestDir = Path.of(System.getenv().getOrDefault("INGEST_DIR", "storage/incoming"));
    Path configDir =
        Path.of(
            System.getenv()
                .getOrDefault(
                    "INGEST_CONFIG_DIR", System.getProperty("user.home") + "/.config/ingest"));
    log.info("Starting with DB_URL={} DB_USER={}", sanitize(rawUrl), user);

    DbConfig dbCfg = new DbConfig(rawUrl, user, password);
    IngestConfig cfg = new IngestConfig(ingestDir, configDir);

    IngestComponent component =
        DaggerIngestComponent.builder().dbConfig(dbCfg).ingestConfig(cfg).build();
    Closeable ds = component.dataSourceCloseable();
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    ds.close();
                  } catch (IOException e) {
                    log.warn("Error closing datasource", e);
                  }
                }));
    IngestService service = component.ingestService();
    FileIngestionService fileService = component.fileIngestionService();
    DirectoryWatchService watch = component.directoryWatchService();
    AccountShorthandParser parser = component.accountShorthandParser();
    CommandLine cmd = new CommandLine(new IngestApp(service, fileService, watch, cfg, parser));
    cmd.setCaseInsensitiveEnumValuesAllowed(true);
    int code = cmd.execute(args);
    System.exit(code);
  }

  public static String sanitize(String url) {
    if (url == null) {
      return "";
    }
    return url.replaceAll("(?<=//)[^/@]+:[^@]+@", "");
  }
}
