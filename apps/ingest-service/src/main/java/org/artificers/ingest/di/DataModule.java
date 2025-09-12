package org.artificers.ingest.di;

import com.zaxxer.hikari.HikariDataSource;
import dagger.Module;
import dagger.Provides;
import java.io.Closeable;
import javax.inject.Singleton;
import org.artificers.ingest.app.IngestApp;
import org.artificers.ingest.config.DbConfig;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderNameStyle;
import org.jooq.conf.RenderQuotedNames;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Module providing database access objects.
 *
 * <p>The {@link HikariDataSource} is a singleton that must be closed when the application shuts
 * down. A {@link Closeable} handle is exposed so callers can register a shutdown hook or otherwise
 * ensure the datasource is closed.
 */
@Module
public interface DataModule {
  Logger LOG = LoggerFactory.getLogger(DataModule.class);

  @Provides
  @Singleton
  static HikariDataSource dataSource(DbConfig cfg) {
    LOG.info(
        "Initializing datasource with url={} user={}", IngestApp.sanitize(cfg.url()), cfg.user());
    HikariDataSource ds = new HikariDataSource();
    ds.setJdbcUrl(JdbcUrl.from(cfg.url()));
    ds.setUsername(cfg.user());
    ds.setPassword(cfg.password());
    return ds;
  }

  @Provides
  @Singleton
  static Closeable dataSourceCloser(HikariDataSource ds) {
    return ds::close;
  }

  @Provides
  @Singleton
  static DSLContext dslContext(HikariDataSource ds) {
    Settings settings =
        new Settings()
            .withRenderQuotedNames(RenderQuotedNames.NEVER)
            .withRenderNameStyle(RenderNameStyle.LOWER);
    return DSL.using(ds, SQLDialect.POSTGRES, settings);
  }
}
