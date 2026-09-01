package br.com.gestpro.nota.provider;

public class FiscalProviderException extends RuntimeException {
    private final boolean resultadoDesconhecido;
    public FiscalProviderException(String message, boolean resultadoDesconhecido, Throwable cause) {
        super(message, cause); this.resultadoDesconhecido = resultadoDesconhecido;
    }
    public boolean isResultadoDesconhecido() { return resultadoDesconhecido; }
}
