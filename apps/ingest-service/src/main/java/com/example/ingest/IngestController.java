package com.example.ingest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

@RestController
public class IngestController {
    private final IngestService service;

    public IngestController(IngestService service) {
        this.service = service;
    }

    @PostMapping("/ingest/file")
    public ResponseEntity<Void> ingest(@RequestParam("path") String path) {
        boolean ok = service.ingestFile(Path.of(path));
        return ok ? ResponseEntity.ok().build() : ResponseEntity.internalServerError().build();
    }
}
