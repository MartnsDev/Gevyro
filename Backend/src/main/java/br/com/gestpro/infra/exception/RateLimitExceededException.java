package br.com.gestpro.infra.exception;

public class RateLimitExceededException extends RuntimeException {
    private final long retryAfterSeconds;
    private final String path;
    public RateLimitExceededException(long retryAfterSeconds, String path) {
        super("Limite de requisições excedido. Tente novamente em instantes.");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds); this.path = path;
    }
    public long getRetryAfterSeconds() { return retryAfterSeconds; }
    public String getPath() { return path; }
}
