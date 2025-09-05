package org.artificers.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.util.Arrays;

/** Application entry point wiring the Dagger component. */
public final class IngestApplication {
    private static final Logger log = LoggerFactory.getLogger(IngestApplication.class);

    public static void main(String[] args) throws Exception {
        String rawUrl = System.getenv("DB_URL");
        String user = System.getenv("DB_USER");
        log.info("Starting with DB_URL={} DB_USER={}", sanitize(rawUrl), user);
        IngestComponent component = DaggerIngestComponent.create();
        IngestService service = component.ingestService();
        if (!processArgs(service, args)) {
            try (DirectoryWatchService watch = component.directoryWatchService()) {
                watch.start();
                Thread.currentThread().join();
            }
        }
    }

    static boolean processArgs(IngestService service, String[] args) throws Exception {
        for (String a : args) {
            if (a.startsWith("--file=")) {
                Path p = Path.of(a.substring("--file=".length()));
                String shorthand = AccountResolver.extractShorthand(p);
                boolean ok = shorthand != null && service.ingestFile(p, shorthand);
                if (!ok) {
                    log.warn("Ingestion failed for {}", p);
                }
                return true;
            }
        }
        boolean scan = Arrays.asList(args).contains("--mode=scan");
        if (scan) {
            String input = "storage/incoming";
            for (String a : args) {
                if (a.startsWith("--input=")) {
                    input = a.substring("--input=".length());
                }
            }
            service.scanAndIngest(Path.of(input));
            return true;
        }
        return false;
    }

    static String sanitize(String url) {
        if (url == null) {
            return "";
        }
        return url.replaceAll("(?<=//)[^/@]+:[^@]+@", "");
    }
}
