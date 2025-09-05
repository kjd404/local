package org.artificers.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/** Application entry point using Dagger and Picocli. */
@Command(name = "ingest", mixinStandardHelpOptions = true)
public final class IngestApp implements Callable<Integer> {
    private static final Logger log = LoggerFactory.getLogger(IngestApp.class);

    @Option(names = "--file", description = "Path to a CSV file")
    Path file;

    @Option(names = "--mode", description = "Execution mode")
    String mode;

    @Option(names = "--input", description = "Directory to scan")
    Path input;

    private final IngestService service;
    private final DirectoryWatchService watchService;
    private final IngestConfig config;

    public IngestApp(IngestService service, DirectoryWatchService watchService, IngestConfig config) {
        this.service = service;
        this.watchService = watchService;
        this.config = config;
    }

    @Override
    public Integer call() throws Exception {
        if (file != null) {
            String shorthand = AccountResolver.extractShorthand(file);
            boolean ok = shorthand != null && service.ingestFile(file, shorthand);
            if (!ok) {
                log.warn("Ingestion failed for {}", file);
            }
            return 0;
        }
        if ("scan".equals(mode)) {
            Path dir = input != null ? input : config.ingestDir();
            service.scanAndIngest(dir);
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
        Path configDir = Path.of(System.getenv().getOrDefault("INGEST_CONFIG_DIR",
                System.getProperty("user.home") + "/.config/ingest"));
        log.info("Starting with DB_URL={} DB_USER={}", sanitize(rawUrl), user);

        DbConfig dbCfg = new DbConfig(rawUrl, user, password);
        IngestConfig cfg = new IngestConfig(ingestDir, configDir);

        IngestComponent component = DaggerIngestComponent.builder()
                .dbConfig(dbCfg)
                .ingestConfig(cfg)
                .build();
        IngestService service = component.ingestService();
        DirectoryWatchService watch = component.directoryWatchService();
        int code = new CommandLine(new IngestApp(service, watch, cfg)).execute(args);
        System.exit(code);
    }

    static String sanitize(String url) {
        if (url == null) {
            return "";
        }
        return url.replaceAll("(?<=//)[^/@]+:[^@]+@", "");
    }
}
