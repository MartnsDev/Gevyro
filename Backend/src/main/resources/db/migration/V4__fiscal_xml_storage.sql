CREATE TABLE IF NOT EXISTS xmls_fiscais (
    id BIGINT NOT NULL AUTO_INCREMENT,
    empresa_id BIGINT NOT NULL,
    documento_id BIGINT NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    conteudo_cifrado LONGBLOB NOT NULL,
    nonce BINARY(12) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    layout_versao VARCHAR(40) NOT NULL,
    provider VARCHAR(40) NOT NULL,
    criado_em DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_xml_fiscal_documento_tipo UNIQUE (documento_id, tipo),
    CONSTRAINT uk_xml_fiscal_sha256 UNIQUE (sha256),
    CONSTRAINT fk_xml_fiscal_empresa FOREIGN KEY (empresa_id) REFERENCES empresas (id),
    CONSTRAINT fk_xml_fiscal_documento FOREIGN KEY (documento_id) REFERENCES notas_fiscais (id),
    INDEX idx_xml_fiscal_empresa (empresa_id, criado_em)
) ENGINE=InnoDB;
