package org.artificers.ingest.cli;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.artificers.ingest.config.DbConfig;
import org.artificers.ingest.di.IngestComponent;
import org.artificers.ingest.config.IngestConfig;

class NewAccountCliTest {
    @Test
    void copiesTemplate() throws IOException {
        Path dir = Files.createTempDirectory("ingest-test");
        Path file = NewAccountCli.copyTemplate(dir, "xx", false);
        assertThat(Files.exists(file)).isTrue();
        assertThatThrownBy(() -> NewAccountCli.copyTemplate(dir, "xx", false))
                .isInstanceOf(IOException.class);
    }

    @Test
    void copyTemplateForceOverwrites() throws IOException {
        Path dir = Files.createTempDirectory("ingest-test");
        Path file = NewAccountCli.copyTemplate(dir, "xx", false);
        Files.writeString(file, "custom");
        Path file2 = NewAccountCli.copyTemplate(dir, "xx", true);
        assertThat(Files.exists(file2)).isTrue();
    }

    @Test
    void insertAccountIsIdempotent() {
        DSLContext dsl = DSL.using("jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_UPPER=false", "sa", "");
        dsl.execute("drop view if exists transactions_view");
        dsl.execute("drop table if exists transactions");
        dsl.execute("drop table if exists accounts");
        dsl.execute("create table accounts (id serial primary key, institution varchar not null, external_id varchar not null, display_name varchar not null, created_at timestamp, updated_at timestamp)");
        long id1 = NewAccountCli.insertAccount(dsl, "ch", "1234", "test");
        long id2 = NewAccountCli.insertAccount(dsl, "ch", "1234", "ignored");
        assertThat(id1).isEqualTo(id2);
        assertThat(dsl.fetchCount(DSL.table("accounts"))).isEqualTo(1);
    }

    @Test
    void daggerConstructsCli() throws Exception {
        Path configDir = Files.createTempDirectory("ingest-test");
        DbConfig db = new DbConfig("jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_UPPER=false", "sa", "");
        IngestConfig cfg = new IngestConfig(Path.of("storage/incoming"), configDir);
        Class<?> comp = Class.forName("org.artificers.ingest.di.DaggerIngestComponent");
        IngestComponent.Builder builder = (IngestComponent.Builder) comp.getMethod("builder").invoke(null);
        IngestComponent component = builder
                .dbConfig(db)
                .ingestConfig(cfg)
                .build();
        assertThat(component.newAccountCli()).isNotNull();
    }
}
