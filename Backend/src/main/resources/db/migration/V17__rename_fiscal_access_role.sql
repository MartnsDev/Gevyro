ALTER TABLE fiscal_company_access
    DROP CHECK ck_fiscal_access_role;

ALTER TABLE fiscal_company_access
    RENAME COLUMN `role` TO fiscal_role;

ALTER TABLE fiscal_company_access
    ADD CONSTRAINT ck_fiscal_access_role
        CHECK (fiscal_role IN ('ADMINISTRADOR','FISCAL','OPERADOR','CONTADOR','SOMENTE_LEITURA'));
