ALTER TABLE notas_fiscais ADD COLUMN venda_id BIGINT NULL;
ALTER TABLE notas_fiscais ADD COLUMN caixa_id BIGINT NULL;
ALTER TABLE notas_fiscais ADD CONSTRAINT uk_nota_venda UNIQUE (venda_id);
ALTER TABLE notas_fiscais ADD CONSTRAINT fk_nota_venda FOREIGN KEY (venda_id) REFERENCES venda (id);
ALTER TABLE notas_fiscais ADD CONSTRAINT fk_nota_caixa FOREIGN KEY (caixa_id) REFERENCES caixas (id);
CREATE INDEX idx_nota_empresa_venda ON notas_fiscais (empresa_id, venda_id);
