package br.com.gestpro.nota;

public enum NotaFiscalStatus {
    DIGITACAO("Em digitação"),
    PENDENTE_EMISSAO("Aguardando emissão"),
    VALIDANDO("Validando"),
    PROCESSANDO("Processando"),
    AUTORIZADA("Autorizada"),
    REJEITADA("Rejeitada"),
    ERRO_TECNICO("Erro técnico"),
    CANCELADA("Cancelada"),
    INUTILIZADA("Inutilizada"),
    CONTINGENCIA("Em contingência");

    private final String descricao;

    NotaFiscalStatus(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() { return descricao; }
}
