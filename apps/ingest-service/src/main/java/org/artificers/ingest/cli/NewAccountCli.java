package org.artificers.ingest.cli;

import org.artificers.jooq.tables.Accounts;
import org.jooq.DSLContext;
import org.jooq.Record1;
import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.Scanner;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "new-account", description = "Create a new account and mapping template", mixinStandardHelpOptions = true)
public class NewAccountCli implements Callable<Integer> {
    @CommandLine.Option(names = "--force", description = "overwrite existing mapping file")
    boolean force;

    private final DSLContext dsl;
    private final Path configDir;

    public NewAccountCli(DSLContext dsl, Path configDir) {
        this.dsl = dsl;
        this.configDir = configDir;
    }

    @Override
    public Integer call() throws Exception {
        Scanner scanner = new Scanner(System.in);
        String institution = prompt(scanner, "Institution code");
        String externalId = prompt(scanner, "Last-four external ID");
        String displayName = prompt(scanner, "Display name");
        String currency = prompt(scanner, "Currency (optional)");

        long id = insertAccount(dsl, institution, externalId, displayName);
        System.out.printf("Account ID: %d%n", id);
        copyTemplate(configDir, institution, force);
        return 0;
    }

    private String prompt(Scanner s, String msg) {
        System.out.print(msg + ": ");
        return s.nextLine().trim();
    }

    public static long insertAccount(DSLContext ctx, String institution, String externalId, String displayName) {
        Record1<Long> existing = ctx.select(Accounts.ACCOUNTS.ID)
                .from(Accounts.ACCOUNTS)
                .where(Accounts.ACCOUNTS.INSTITUTION.eq(institution)
                        .and(Accounts.ACCOUNTS.EXTERNAL_ID.eq(externalId)))
                .fetchOne();
        if (existing != null) {
            return existing.value1();
        }
        OffsetDateTime now = OffsetDateTime.now();
        return ctx.insertInto(Accounts.ACCOUNTS)
                .set(Accounts.ACCOUNTS.INSTITUTION, institution)
                .set(Accounts.ACCOUNTS.EXTERNAL_ID, externalId)
                .set(Accounts.ACCOUNTS.DISPLAY_NAME, displayName)
                .set(Accounts.ACCOUNTS.CREATED_AT, now)
                .set(Accounts.ACCOUNTS.UPDATED_AT, now)
                .returningResult(Accounts.ACCOUNTS.ID)
                .fetchOne()
                .value1();
    }

    public static Path copyTemplate(Path configDir, String institution, boolean force) throws IOException {
        Path mappingDir = configDir.resolve("mappings");
        Files.createDirectories(mappingDir);
        Path target = mappingDir.resolve(institution + ".yaml");
        if (Files.exists(target) && !force) {
            throw new IOException("Mapping file " + target + " exists");
        }
        try (InputStream in = NewAccountCli.class.getResourceAsStream("/mappings/example.yaml")) {
            if (in == null) {
                throw new IOException("example.yaml resource missing");
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }


}
