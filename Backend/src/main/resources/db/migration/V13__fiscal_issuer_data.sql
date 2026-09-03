ALTER TABLE configuracoes_fiscais
    ADD COLUMN inscricao_municipal VARCHAR(20) NULL,
    ADD COLUMN cnae VARCHAR(7) NULL,
    ADD COLUMN codigo_ibge VARCHAR(7) NULL,
    ADD COLUMN complemento VARCHAR(60) NULL,
    ADD COLUMN email_fiscal VARCHAR(254) NULL;
