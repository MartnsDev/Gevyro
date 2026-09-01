CREATE TABLE IF NOT EXISTS sequencias_fiscais (
    id BIGINT NOT NULL AUTO_INCREMENT,
    empresa_id BIGINT NOT NULL,
    tipo VARCHAR(10) NOT NULL,
    serie VARCHAR(3) NOT NULL,
    ambiente VARCHAR(15) NOT NULL,
    proximo_numero BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sequencia_fiscal_empresa_tipo_serie_ambiente
        UNIQUE (empresa_id, tipo, serie, ambiente),
    CONSTRAINT fk_sequencia_fiscal_empresa FOREIGN KEY (empresa_id) REFERENCES empresas (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS certificados_digitais (
    id BIGINT NOT NULL AUTO_INCREMENT,
    empresa_id BIGINT NOT NULL,
    arquivo_cifrado LONGBLOB NOT NULL,
    arquivo_nonce BINARY(12) NOT NULL,
    senha_cifrada BLOB NOT NULL,
    senha_nonce BINARY(12) NOT NULL,
    titular VARCHAR(500) NOT NULL,
    emissor VARCHAR(500) NOT NULL,
    numero_serie VARCHAR(100) NOT NULL,
    valido_de DATETIME(6) NOT NULL,
    valido_ate DATETIME(6) NOT NULL,
    atualizado_em DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_certificado_empresa UNIQUE (empresa_id),
    CONSTRAINT fk_certificado_empresa FOREIGN KEY (empresa_id) REFERENCES empresas (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS fiscal_idempotency (
    id BIGINT NOT NULL AUTO_INCREMENT,
    empresa_id BIGINT NOT NULL,
    documento_id BIGINT NOT NULL,
    operacao VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(30) NOT NULL,
    criado_em DATETIME(6) NOT NULL,
    atualizado_em DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_fiscal_idempotency_empresa_operacao_chave
        UNIQUE (empresa_id, operacao, idempotency_key),
    CONSTRAINT fk_fiscal_idempotency_empresa FOREIGN KEY (empresa_id) REFERENCES empresas (id),
    CONSTRAINT fk_fiscal_idempotency_documento FOREIGN KEY (documento_id) REFERENCES notas_fiscais (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS fiscal_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    empresa_id BIGINT NOT NULL,
    documento_id BIGINT NULL,
    acao VARCHAR(80) NOT NULL,
    ator VARCHAR(320) NOT NULL,
    ambiente VARCHAR(20) NOT NULL,
    resultado VARCHAR(40) NOT NULL,
    detalhes VARCHAR(1000) NULL,
    correlation_id VARCHAR(100) NOT NULL,
    hash_anterior CHAR(64) NOT NULL,
    hash_registro CHAR(64) NOT NULL,
    criado_em DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_fiscal_audit_hash UNIQUE (hash_registro),
    CONSTRAINT fk_fiscal_audit_empresa FOREIGN KEY (empresa_id) REFERENCES empresas (id),
    CONSTRAINT fk_fiscal_audit_documento FOREIGN KEY (documento_id) REFERENCES notas_fiscais (id),
    INDEX idx_fiscal_audit_empresa_data (empresa_id, criado_em),
    INDEX idx_fiscal_audit_documento (documento_id)
) ENGINE=InnoDB;
