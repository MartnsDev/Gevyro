CREATE TABLE fiscal_deliveries (
    id BIGINT NOT NULL AUTO_INCREMENT, empresa_id BIGINT NOT NULL, documento_id BIGINT NOT NULL,
    canal VARCHAR(20) NOT NULL, status VARCHAR(30) NOT NULL, destinatario_cifrado BLOB NOT NULL,
    destinatario_nonce BINARY(12) NOT NULL, dedup_key CHAR(64) NOT NULL,
    tentativas INT NOT NULL DEFAULT 0, max_tentativas INT NOT NULL DEFAULT 5,
    proxima_tentativa_em DATETIME(6) NULL, criado_em DATETIME(6) NOT NULL,
    atualizado_em DATETIME(6) NOT NULL, versao BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id), CONSTRAINT uk_fiscal_delivery_dedup UNIQUE (dedup_key),
    CONSTRAINT fk_fiscal_delivery_empresa FOREIGN KEY (empresa_id) REFERENCES empresas (id),
    CONSTRAINT fk_fiscal_delivery_documento FOREIGN KEY (documento_id) REFERENCES notas_fiscais (id),
    INDEX idx_fiscal_delivery_queue (status, proxima_tentativa_em),
    INDEX idx_fiscal_delivery_empresa (empresa_id, criado_em)
) ENGINE=InnoDB;
