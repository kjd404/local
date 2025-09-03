package org.artificers.ingest;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IngestControllerTest {
    @Test
    void returnsServerErrorWhenIngestionFails() {
        IngestService service = mock(IngestService.class);
        when(service.ingestFile(any(), any())).thenReturn(false);
        IngestController controller = new IngestController(service);

        ResponseEntity<Void> resp = controller.ingest("/tmp/ch1234.csv");

        assertThat(resp.getStatusCode().is5xxServerError()).isTrue();
    }

    @Test
    void returnsOkWhenIngestionSucceeds() {
        IngestService service = mock(IngestService.class);
        when(service.ingestFile(any(), any())).thenReturn(true);
        IngestController controller = new IngestController(service);

        ResponseEntity<Void> resp = controller.ingest("/tmp/ch1234.csv");

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
