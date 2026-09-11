package br.com.gestpro.infra.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

/** Recupera exclusivamente os incidentes conhecidos e verificáveis das migrations fiscais. */
@Configuration
@Profile("prod")
@Slf4j
public class FailedV12MigrationRecoveryConfig {

    @Bean
    FlywayMigrationStrategy recoverKnownFailedFiscalMigrations(DataSource dataSource) {
        return flyway -> {
            recuperarSeSeguro(dataSource);
            flyway.migrate();
        };
    }

    void recuperarSeSeguro(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            if (!tabelaExiste(connection, "flyway_schema_history")) return;
            recuperarV12SeSeguro(connection);
            recuperarV17SeSeguro(connection);
        } catch (SQLException e) {
            throw new IllegalStateException("Não foi possível verificar com segurança as migrations fiscais.", e);
        }
    }

    private void recuperarV12SeSeguro(Connection connection) throws SQLException {
        int falhasV12 = contarFalhasV12(connection);
        if (falhasV12 == 0) return;
        if (falhasV12 != 1) throw new IllegalStateException(
                "Há mais de um marcador falho para a V12; recuperação automática recusada.");
        if (tabelaExiste(connection, "fiscal_company_access")) throw new IllegalStateException(
                "A V12 deixou estrutura parcial; recuperação automática recusada.");

        removerMarcadorFalho(connection, "12");
        log.warn("Recuperação controlada da V12 concluída; a migration corrigida será aplicada pelo Flyway.");
    }

    private void recuperarV17SeSeguro(Connection connection) throws SQLException {
        int falhasV17 = contarFalhas(connection, "17");
        if (falhasV17 == 0) return;
        if (falhasV17 != 1) throw new IllegalStateException(
                "Há mais de um marcador falho para a V17; recuperação automática recusada.");
        if (!tabelaExiste(connection, "fiscal_company_access")) throw new IllegalStateException(
                "A tabela esperada pela V17 não existe; recuperação automática recusada.");

        boolean colunaAntigaExiste = colunaExiste(connection, "fiscal_company_access", "role");
        boolean colunaNovaExiste = colunaExiste(connection, "fiscal_company_access", "fiscal_role");
        if (!colunaAntigaExiste || colunaNovaExiste) throw new IllegalStateException(
                "A V17 deixou estado parcial ou inesperado; recuperação automática recusada.");

        removerMarcadorFalho(connection, "17");
        log.warn("Recuperação controlada da V17 concluída; a migration corrigida será aplicada pelo Flyway.");
    }

    private void removerMarcadorFalho(Connection connection, String versao) throws SQLException {
        boolean autoCommitOriginal = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM flyway_schema_history WHERE version = ? AND success = FALSE")) {
            delete.setString(1, versao);
            if (delete.executeUpdate() != 1) throw new IllegalStateException(
                    "O histórico da V" + versao + " mudou durante a recuperação; operação recusada.");
            connection.commit();
        } catch (RuntimeException | SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommitOriginal);
        }
    }

    private int contarFalhasV12(Connection connection) throws SQLException {
        return contarFalhas(connection, "12");
    }

    private int contarFalhas(Connection connection, String versao) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = FALSE")) {
            query.setString(1, versao);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) throw new IllegalStateException("Histórico Flyway ilegível.");
                return result.getInt(1);
            }
        }
    }

    private boolean colunaExiste(Connection connection, String tabela, String coluna) throws SQLException {
        return colunaExisteExata(connection, tabela, coluna)
                || colunaExisteExata(connection, tabela.toUpperCase(Locale.ROOT), coluna.toUpperCase(Locale.ROOT));
    }

    private boolean colunaExisteExata(Connection connection, String tabela, String coluna) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(
                connection.getCatalog(), null, tabela, coluna)) {
            return columns.next();
        }
    }

    private boolean tabelaExiste(Connection connection, String nome) throws SQLException {
        if (existe(connection, nome)) return true;
        return existe(connection, nome.toUpperCase(Locale.ROOT));
    }

    private boolean existe(Connection connection, String nome) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(
                connection.getCatalog(), null, nome, new String[]{"TABLE"})) {
            return tables.next();
        }
    }
}
