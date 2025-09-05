package org.artificers.ingest;

import com.zaxxer.hikari.HikariDataSource;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

/** Module providing database access objects. */
@Module
public interface DataModule {
    @Provides
    @Singleton
    static HikariDataSource dataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(JdbcUrl.from(System.getenv("DB_URL")));
        ds.setUsername(System.getenv("DB_USER"));
        ds.setPassword(System.getenv("DB_PASSWORD"));
        return ds;
    }

    @Provides
    @Singleton
    static DSLContext dslContext(HikariDataSource ds) {
        return DSL.using(ds, SQLDialect.POSTGRES);
    }
}
