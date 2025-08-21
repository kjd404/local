package com.example.teller;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.List;

/**
 * Assembles a {@link TellerClient} with the necessary HTTP and retry components.
 */
public final class TellerClientFactory {

    private final Path certPath;
    private final Path keyPath;

    public TellerClientFactory(Path certPath, Path keyPath) {
        this.certPath = certPath;
        this.keyPath = keyPath;
    }

    public TellerClient create(List<String> tokens) {
        MtlsHttpClientFactory httpClientFactory = new MtlsHttpClientFactory(certPath, keyPath);
        RequestExecutor executor = new RetryingRequestExecutor(httpClientFactory.create(), new ObjectMapper());
        TellerApi api = new HttpTellerApi(executor);
        return new TellerClient(tokens, api);
    }
}

