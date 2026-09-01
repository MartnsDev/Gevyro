CREATE TABLE IF NOT EXISTS configuracoes_fiscais (
    id BIGINT NOT NULL AUTO_INCREMENT,
    empresa_id BIGINT NOT NULL,
    inscricao_estadual VARCHAR(20) NULL,
    regime_tributario VARCHAR(40) NOT NULL,
    ambiente VARCHAR(15) NOT NULL,
    serie_nfe VARCHAR(3) NOT NULL,
    serie_nfce VARCHAR(3) NOT NULL,
    csc_id VARCHAR(20) NULL,
    csc_cifrado BLOB NULL,
    csc_nonce BINARY(12) NULL,
    atualizado_em DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_configuracao_fiscal_empresa UNIQUE (empresa_id),
    CONSTRAINT fk_configuracao_fiscal_empresa FOREIGN KEY (empresa_id) REFERENCES empresas (id)
) ENGINE=InnoDB;
