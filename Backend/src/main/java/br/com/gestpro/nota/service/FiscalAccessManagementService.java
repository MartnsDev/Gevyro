package br.com.gestpro.nota.service;

import br.com.gestpro.auth.model.Usuario;
import br.com.gestpro.auth.repository.UsuarioRepository;
import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.FiscalPermission;
import br.com.gestpro.nota.dto.FiscalAccessRequest;
import br.com.gestpro.nota.dto.FiscalAccessResponse;
import br.com.gestpro.nota.model.FiscalCompanyAccess;
import br.com.gestpro.nota.repository.FiscalCompanyAccessRepository;
import br.com.gestpro.plano.StatusAcesso;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class FiscalAccessManagementService {
    private static final int MAX_COLABORADORES = 25;
    private final FiscalCompanyAccessRepository repository;
    private final UsuarioRepository usuarios;
    private final FiscalAuthorizationService authorization;
    private final FiscalAuditService audit;

    @Transactional(readOnly = true)
    public List<FiscalAccessResponse> listar(Long empresaId, String ator) {
        authorization.exigir(empresaId, ator, FiscalPermission.GERENCIAR_ACESSOS);
        List<FiscalCompanyAccess> acessos = repository.findByEmpresaIdOrderByIdAsc(empresaId);
        Map<Long, String> emails = new HashMap<>();
        usuarios.findAllById(acessos.stream().map(FiscalCompanyAccess::getUsuarioId).toList())
                .forEach(u -> emails.put(u.getId(), u.getEmail()));
        return acessos.stream().map(a -> resposta(a, emails.get(a.getUsuarioId()))).toList();
    }

    @Transactional
    public FiscalAccessResponse conceder(Long empresaId, FiscalAccessRequest request, String ator) {
        var empresa = authorization.exigir(empresaId, ator, FiscalPermission.GERENCIAR_ACESSOS);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (empresa.getDono().getEmail().equalsIgnoreCase(email))
            throw erro("O proprietário já possui todas as permissões fiscais.", HttpStatus.CONFLICT);
        Usuario usuario = usuarios.findByEmail(email)
                .orElseThrow(() -> erro("O colaborador deve possuir uma conta ativa na Gevyro.", HttpStatus.UNPROCESSABLE_ENTITY));
        if (!usuario.isEmailConfirmado() || usuario.getStatusAcesso() != StatusAcesso.ATIVO)
            throw erro("O colaborador deve possuir uma conta ativa e confirmada na Gevyro.", HttpStatus.UNPROCESSABLE_ENTITY);
        FiscalCompanyAccess acesso = repository.findByEmpresaIdAndUsuarioId(empresaId, usuario.getId()).orElse(null);
        if ((acesso == null || !acesso.isAtivo()) && repository.countByEmpresaIdAndAtivoTrue(empresaId) >= MAX_COLABORADORES)
            throw erro("Limite de colaboradores fiscais atingido.", HttpStatus.CONFLICT);
        if (acesso == null) acesso = FiscalCompanyAccess.builder().empresaId(empresaId).usuarioId(usuario.getId()).build();
        acesso.setRole(request.role()); acesso.setAtivo(true);
        acesso = repository.save(acesso);
        audit.registrar(empresaId, null, "ACESSO_FISCAL_CONCEDIDO", ator, "SUCESSO",
                "usuarioId=" + usuario.getId() + ";papel=" + request.role());
        return resposta(acesso, usuario.getEmail());
    }

    @Transactional
    public void revogar(Long empresaId, Long acessoId, String ator) {
        authorization.exigir(empresaId, ator, FiscalPermission.GERENCIAR_ACESSOS);
        FiscalCompanyAccess acesso = repository.findByIdAndEmpresaId(acessoId, empresaId)
                .orElseThrow(() -> erro("Acesso fiscal não encontrado.", HttpStatus.NOT_FOUND));
        acesso.setAtivo(false); repository.save(acesso);
        audit.registrar(empresaId, null, "ACESSO_FISCAL_REVOGADO", ator, "SUCESSO", "usuarioId=" + acesso.getUsuarioId());
    }

    private FiscalAccessResponse resposta(FiscalCompanyAccess a, String email) {
        return new FiscalAccessResponse(a.getId(), a.getUsuarioId(), email, a.getRole(), a.isAtivo());
    }
    private ApiException erro(String mensagem, HttpStatus status) { return new ApiException(mensagem, status, "/api/fiscal/acessos"); }
}
