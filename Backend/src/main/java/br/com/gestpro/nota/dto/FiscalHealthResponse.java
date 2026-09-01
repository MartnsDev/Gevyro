package br.com.gestpro.nota.dto;

public record FiscalHealthResponse(String provider, String circuitBreaker, long chamadasFalhas,
                                   long chamadasSucesso, int filaPendente) {}
