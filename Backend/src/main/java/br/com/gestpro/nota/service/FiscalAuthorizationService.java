package br.com.gestpro.nota.service;

import br.com.gestpro.empresa.model.Empresa;
import br.com.gestpro.empresa.repository.EmpresaRepository;
import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.FiscalPermission;
import br.com.gestpro.nota.repository.FiscalCompanyAccessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class FiscalAuthorizationService {
    private final EmpresaRepository empresas;
    private final FiscalCompanyAccessRepository acessos;

    @Transactional(readOnly = true)
    public Empresa exigir(Long empresaId, String email, FiscalPermission permission) {
        if (empresaId == null || email == null || email.isBlank()) throw negado();
        Empresa empresa = empresas.findByIdWithDono(empresaId)
                .orElseThrow(() -> new ApiException("Empresa não encontrada.", HttpStatus.NOT_FOUND, "/api/fiscal"));
        if (!Boolean.TRUE.equals(empresa.getAtivo()))
            throw new ApiException("Esta empresa está arquivada.", HttpStatus.CONFLICT, "/api/fiscal");
        if (empresa.getDono() != null && empresa.getDono().getEmail().equalsIgnoreCase(email)) return empresa;
        boolean permitido = acessos.findActive(empresaId, email)
                .map(acesso -> acesso.getRole().permite(permission)).orElse(false);
        if (!permitido) throw negado();
        return empresa;
    }

    private ApiException negado() {
        return new ApiException("Sem permissão para esta operação fiscal.", HttpStatus.FORBIDDEN, "/api/fiscal");
    }
}
