CREATE TABLE fiscal_company_access (
    id BIGINT NOT NULL AUTO_INCREMENT,
    empresa_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    role VARCHAR(24) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_fiscal_access_company_user UNIQUE (empresa_id, usuario_id),
    CONSTRAINT fk_fiscal_access_company FOREIGN KEY (empresa_id) REFERENCES empresas(id),
    CONSTRAINT fk_fiscal_access_user FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT ck_fiscal_access_role CHECK (role IN ('ADMINISTRADOR','FISCAL','OPERADOR','CONTADOR','SOMENTE_LEITURA')),
    PRIMARY KEY (id)
);

CREATE INDEX idx_fiscal_access_user_active ON fiscal_company_access (usuario_id, ativo);
