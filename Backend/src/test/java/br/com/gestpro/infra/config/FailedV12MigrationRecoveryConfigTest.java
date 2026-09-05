package br.com.gestpro.infra.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.*;

class FailedV12MigrationRecoveryConfigTest {

    @Test
    void removeSomenteMarcadorV12FalhoQuandoNaoHaDdlParcial() {
        DataSource dataSource = banco("recuperacao_segura");
        JdbcTemplate jdbc = prepararHistorico(dataSource);
        jdbc.update("INSERT INTO flyway_schema_history(version, success) VALUES ('11', TRUE), ('12', FALSE)");

        new FailedV12MigrationRecoveryConfig().recuperarSeSeguro(dataSource);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='12'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='11'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void ficaInerteDepoisQueV12NaoEstaFalha() {
        DataSource dataSource = banco("recuperacao_inerte");
        JdbcTemplate jdbc = prepararHistorico(dataSource);
        jdbc.update("INSERT INTO flyway_schema_history(version, success) VALUES ('12', TRUE)");

        new FailedV12MigrationRecoveryConfig().recuperarSeSeguro(dataSource);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class)).isEqualTo(1);
    }

    @Test
    void recusaApagarHistoricoQuandoTabelaV12Existe() {
        DataSource dataSource = banco("recuperacao_parcial");
        JdbcTemplate jdbc = prepararHistorico(dataSource);
        jdbc.update("INSERT INTO flyway_schema_history(version, success) VALUES ('12', FALSE)");
        jdbc.execute("CREATE TABLE fiscal_company_access (id BIGINT PRIMARY KEY)");

        assertThatThrownBy(() -> new FailedV12MigrationRecoveryConfig().recuperarSeSeguro(dataSource))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("estrutura parcial");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='12'", Integer.class))
                .isEqualTo(1);
    }

    private JdbcTemplate prepararHistorico(DataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE flyway_schema_history (version VARCHAR(50), success BOOLEAN NOT NULL)");
        return jdbc;
    }

    private DataSource banco(String nome) {
        return new DriverManagerDataSource("jdbc:h2:mem:" + nome + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    }
}
