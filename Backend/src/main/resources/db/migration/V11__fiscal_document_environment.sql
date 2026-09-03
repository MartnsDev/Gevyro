ALTER TABLE notas_fiscais
    ADD COLUMN ambiente VARCHAR(24) NOT NULL DEFAULT 'LEGADO_DESCONHECIDO';

CREATE INDEX idx_nota_empresa_ambiente_data
    ON notas_fiscais (empresa_id, ambiente, created_at);
