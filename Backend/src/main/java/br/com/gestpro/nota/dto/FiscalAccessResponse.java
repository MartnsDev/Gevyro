package br.com.gestpro.nota.dto;

import br.com.gestpro.nota.FiscalRole;

public record FiscalAccessResponse(Long id, Long usuarioId, String email, FiscalRole role, boolean ativo) {}
