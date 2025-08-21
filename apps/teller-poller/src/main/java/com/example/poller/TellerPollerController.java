package com.example.poller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

@RestController
public class TellerPollerController {
    private final TellerPollerService service;

    public TellerPollerController(TellerPollerService service) {
        this.service = service;
    }

    @PostMapping("/ingest/file")
    public ResponseEntity<Void> ingest(@RequestParam("path") String path) throws Exception {
        service.ingestFile(Path.of(path));
        return ResponseEntity.ok().build();
    }
}
