ALTER TABLE notas_fiscais MODIFY COLUMN chave_acesso VARCHAR(50);
ALTER TABLE notas_fiscais ADD COLUMN nfse_competencia DATE NULL;
ALTER TABLE notas_fiscais ADD COLUMN nfse_codigo_municipio_prestacao VARCHAR(7) NULL;
ALTER TABLE notas_fiscais ADD COLUMN nfse_codigo_tributacao_nacional VARCHAR(6) NULL;
ALTER TABLE notas_fiscais ADD COLUMN nfse_opcao_simples SMALLINT NULL;
ALTER TABLE notas_fiscais ADD COLUMN nfse_regime_especial SMALLINT NULL;
ALTER TABLE notas_fiscais ADD COLUMN nfse_tributacao_issqn SMALLINT NULL;
ALTER TABLE notas_fiscais ADD COLUMN nfse_retencao_issqn SMALLINT NULL;
ALTER TABLE notas_fiscais ADD COLUMN nfse_aliquota_issqn DECIMAL(5,2) NULL;
