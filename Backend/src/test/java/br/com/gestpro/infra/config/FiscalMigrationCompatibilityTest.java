package br.com.gestpro.infra.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.sql.Connection;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

class FiscalMigrationCompatibilityTest {

    @Test
    void v12UsaIdentidadeCompativelComMySql() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:v12_mysql;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("CREATE TABLE empresas (id BIGINT PRIMARY KEY)");
            connection.createStatement().execute("CREATE TABLE usuarios (id BIGINT PRIMARY KEY)");
        }

        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V12__fiscal_role_access.sql")).execute(dataSource);

        try (Connection connection = dataSource.getConnection();
             ResultSet columns = connection.getMetaData().getColumns(null, null,
                     "FISCAL_COMPANY_ACCESS", "ID")) {
            assertThat(columns.next()).isTrue();
            assertThat(columns.getString("IS_AUTOINCREMENT")).isEqualTo("YES");
        }
    }
}
