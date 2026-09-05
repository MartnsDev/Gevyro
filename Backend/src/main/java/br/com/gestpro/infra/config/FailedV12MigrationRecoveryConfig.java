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

/** Recupera exclusivamente a V12 que foi publicada com sintaxe incompatível com MySQL. */
@Configuration
@Profile("prod")
@Slf4j
public class FailedV12MigrationRecoveryConfig {

    @Bean
    FlywayMigrationStrategy recoverFailedV12Only(DataSource dataSource) {
        return flyway -> {
            recuperarSeSeguro(dataSource);
            flyway.migrate();
        };
    }

    void recuperarSeSeguro(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            if (!tabelaExiste(connection, "flyway_schema_history")) return;
            int falhasV12 = contarFalhasV12(connection);
            if (falhasV12 == 0) return;
            if (falhasV12 != 1) throw new IllegalStateException(
                    "Há mais de um marcador falho para a V12; recuperação automática recusada.");
            if (tabelaExiste(connection, "fiscal_company_access")) throw new IllegalStateException(
                    "A V12 deixou estrutura parcial; recuperação automática recusada.");

            boolean autoCommitOriginal = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM flyway_schema_history WHERE version = ? AND success = FALSE")) {
                delete.setString(1, "12");
                if (delete.executeUpdate() != 1) throw new IllegalStateException(
                        "O histórico da V12 mudou durante a recuperação; operação recusada.");
                connection.commit();
            } catch (RuntimeException | SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommitOriginal);
            }
            log.warn("Recuperação controlada da V12 concluída; a migration corrigida será aplicada pelo Flyway.");
        } catch (SQLException e) {
            throw new IllegalStateException("Não foi possível verificar com segurança a migration V12.", e);
        }
    }

    private int contarFalhasV12(Connection connection) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = FALSE")) {
            query.setString(1, "12");
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) throw new IllegalStateException("Histórico Flyway ilegível.");
                return result.getInt(1);
            }
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
