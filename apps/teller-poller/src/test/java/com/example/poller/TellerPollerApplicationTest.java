package com.example.poller;

import com.example.teller.TellerClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "DB_USER=sa",
        "DB_PASSWORD=",
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
class TellerPollerApplicationTest {
    @Autowired
    private TellerClient tellerClient;

    @MockBean
    private AccountPollingService accountPollingService;

    @Test
    void contextLoads() {
        assertThat(tellerClient).isNotNull();
    }
}
