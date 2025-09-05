package org.artificers.ingest;

import com.zaxxer.hikari.HikariDataSource;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Module providing database access objects. */
@Module
public interface DataModule {
    Logger LOG = LoggerFactory.getLogger(DataModule.class);

    @Provides
    @Singleton
    static HikariDataSource dataSource(DbConfig cfg) {
        LOG.info("Initializing datasource with url={} user={}", IngestApp.sanitize(cfg.url()), cfg.user());
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(JdbcUrl.from(cfg.url()));
        ds.setUsername(cfg.user());
        ds.setPassword(cfg.password());
        return ds;
    }

    @Provides
    @Singleton
    static DSLContext dslContext(HikariDataSource ds) {
        return DSL.using(ds, SQLDialect.POSTGRES);
    }
}
