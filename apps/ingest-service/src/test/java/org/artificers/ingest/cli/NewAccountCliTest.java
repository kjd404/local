package org.artificers.ingest.cli;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NewAccountCliTest {
    @Test
    void copiesTemplate() throws IOException {
        Path dir = Files.createTempDirectory("ingest-test");
        Path file = NewAccountCli.copyTemplate(dir, "xx", false);
        assertThat(Files.exists(file)).isTrue();
        assertThatThrownBy(() -> NewAccountCli.copyTemplate(dir, "xx", false))
                .isInstanceOf(IOException.class);
    }
}
