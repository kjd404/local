package org.artificers.ingest.service;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DirectoryWatchService implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(DirectoryWatchService.class);

  private final Path directory;
  private final ExecutorService executor;
  private final WatchService watchService;
  private final FileIngestionService fileService;
  private final AccountShorthandParser shorthandParser;

  public DirectoryWatchService(
      FileIngestionService fileService,
      Path dir,
      ExecutorService executor,
      WatchService watchService,
      AccountShorthandParser shorthandParser) {
    this.fileService = fileService;
    this.directory = dir.toAbsolutePath();
    this.executor = executor;
    this.watchService = watchService;
    this.shorthandParser = shorthandParser;
  }

  public void start() throws IOException {
    Files.createDirectories(directory);
    log.info("Watching directory {} for new files", directory);
    directory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
    rescanDirectory();
    executor.submit(this::processEvents);
  }

  private void rescanDirectory() {
    try {
      fileService.scanAndIngest(directory);
    } catch (IOException e) {
      log.error("Failed to scan directory {}", directory, e);
    }
  }

  public void stop() {
    executor.shutdownNow();
    try (WatchService ignored = watchService) {
      executor.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (IOException e) {
      log.error("Failed to close watch service", e);
    }
  }

  @Override
  public void close() {
    stop();
  }

  private void processEvents() {
    while (!Thread.currentThread().isInterrupted()) {
      try {
        WatchKey key = watchService.take();
        for (WatchEvent<?> event : key.pollEvents()) {
          if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
            Path filename = (Path) event.context();
            Path file = directory.resolve(filename);
            String shorthand = shorthandParser.extract(file);
            if (shorthand != null) {
              try {
                fileService.ingestFile(file, shorthand);
              } catch (IOException e) {
                log.error("Failed to ingest file {}", file, e);
              }
            }
          }
        }
        boolean valid = key.reset();
        rescanDirectory();
        if (!valid) {
          break;
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      } catch (ClosedWatchServiceException e) {
        break;
      } catch (Exception e) {
        log.error("Watch service error", e);
      }
    }
  }
}
