package br.com.gestpro.infra.config;

import br.com.gestpro.nota.model.FiscalDelivery;
import br.com.gestpro.nota.model.CertificadoDigital;
import br.com.gestpro.nota.model.ConfiguracaoFiscalEmpresa;
import br.com.gestpro.nota.model.EventoFiscal;
import br.com.gestpro.nota.model.FiscalWebhookConfig;
import br.com.gestpro.nota.model.XmlFiscal;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.sql.Connection;
import java.sql.ResultSet;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;

class FiscalMigrationCompatibilityTest {

    @Test
    void noncesMantemTipoBinarioFixoDasMigrations() throws Exception {
        assertBinaryNonce(FiscalDelivery.class, "destinatarioNonce");
        assertBinaryNonce(CertificadoDigital.class, "arquivoNonce");
        assertBinaryNonce(CertificadoDigital.class, "senhaNonce");
        assertBinaryNonce(ConfiguracaoFiscalEmpresa.class, "cscNonce");
        assertBinaryNonce(EventoFiscal.class, "nonce");
        assertBinaryNonce(FiscalWebhookConfig.class, "urlNonce");
        assertBinaryNonce(FiscalWebhookConfig.class, "segredoNonce");
        assertBinaryNonce(XmlFiscal.class, "nonce");
    }

    private void assertBinaryNonce(Class<?> entity, String field) throws Exception {
        Column mapping = entity.getDeclaredField(field).getAnnotation(Column.class);
        assertThat(mapping.columnDefinition()).isEqualTo("BINARY(12)");
        assertThat(mapping.length()).isEqualTo(12);
    }

    @Test
    void v12PermaneceImutavelDepoisDeAplicadaEmProducao() throws Exception {
        ClassPathResource migration = new ClassPathResource("db/migration/V12__fiscal_role_access.sql");
        CRC32 checksum = new CRC32();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                migration.getInputStream(), StandardCharsets.UTF_8))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                checksum.update(line.replace("\uFEFF", "").getBytes(StandardCharsets.UTF_8));
            }
        }

        assertThat((int) checksum.getValue()).isEqualTo(87_968_478);
    }

    @Test
    void dedupKeyMantemMesmoTipoFixoDaMigrationV15() throws Exception {
        Column mapping = FiscalDelivery.class.getDeclaredField("dedupKey").getAnnotation(Column.class);

        assertThat(mapping.columnDefinition()).isEqualTo("CHAR(64)");
        assertThat(mapping.length()).isEqualTo(64);
    }

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
        String migrationV17 = new ClassPathResource("db/migration/V17__rename_fiscal_access_role.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(migrationV17).contains("DROP CHECK ck_fiscal_access_role");
        String migrationV17CompativelComH2 = migrationV17.replace("DROP CHECK", "DROP CONSTRAINT");
        new ResourceDatabasePopulator(new ByteArrayResource(
                migrationV17CompativelComH2.getBytes(StandardCharsets.UTF_8))).execute(dataSource);

        try (Connection connection = dataSource.getConnection();
             ResultSet columns = connection.getMetaData().getColumns(null, null,
                     "FISCAL_COMPANY_ACCESS", "ID")) {
            assertThat(columns.next()).isTrue();
            assertThat(columns.getString("IS_AUTOINCREMENT")).isEqualTo("YES");
        }
        try (Connection connection = dataSource.getConnection();
             ResultSet role = connection.getMetaData().getColumns(null, null,
                     "FISCAL_COMPANY_ACCESS", "FISCAL_ROLE")) {
            assertThat(role.next()).isTrue();
        }
    }
}
