package org.artificers.ingest.csv;

import java.io.Reader;
import java.nio.file.Path;
import java.util.List;
import org.artificers.ingest.model.TransactionRecord;

public interface TransactionCsvReader {
  /** Shorthand institution code used in filenames (e.g. "ch" for Chase). */
  String institution();

  /**
   * Read transactions for the given account. The accountId is taken from the filename shorthand and
   * should be applied to every transaction rather than parsed from the CSV contents.
   */
  List<TransactionRecord> read(Path file, Reader reader, String accountId);
}
