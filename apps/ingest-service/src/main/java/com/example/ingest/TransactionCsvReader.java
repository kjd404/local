package com.example.ingest;

import java.io.Reader;
import java.nio.file.Path;
import java.util.List;

public interface TransactionCsvReader {
    List<TransactionRecord> read(Path file, Reader reader);
}

