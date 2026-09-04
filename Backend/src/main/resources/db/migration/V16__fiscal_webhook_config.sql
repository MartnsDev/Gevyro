CREATE TABLE fiscal_webhook_configs (
    id BIGINT NOT NULL AUTO_INCREMENT, empresa_id BIGINT NOT NULL,
    url_cifrada BLOB NOT NULL, url_nonce BINARY(12) NOT NULL,
    segredo_cifrado BLOB NOT NULL, segredo_nonce BINARY(12) NOT NULL,
    host_aprovado VARCHAR(253) NOT NULL, eventos VARCHAR(255) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT FALSE, atualizado_em DATETIME(6) NOT NULL,
    PRIMARY KEY (id), CONSTRAINT uk_fiscal_webhook_empresa UNIQUE (empresa_id),
    CONSTRAINT fk_fiscal_webhook_empresa FOREIGN KEY (empresa_id) REFERENCES empresas (id)
) ENGINE=InnoDB;
