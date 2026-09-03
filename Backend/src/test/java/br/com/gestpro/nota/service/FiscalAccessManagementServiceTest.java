package br.com.gestpro.nota.service;

import br.com.gestpro.auth.model.Usuario;
import br.com.gestpro.auth.repository.UsuarioRepository;
import br.com.gestpro.empresa.model.Empresa;
import br.com.gestpro.nota.*;
import br.com.gestpro.nota.dto.FiscalAccessRequest;
import br.com.gestpro.nota.model.FiscalCompanyAccess;
import br.com.gestpro.nota.repository.FiscalCompanyAccessRepository;
import br.com.gestpro.plano.StatusAcesso;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FiscalAccessManagementServiceTest {
    @Test void concedePapelSomenteParaContaConfirmadaEAuditaSemEmail() {
        FiscalCompanyAccessRepository repository = mock(FiscalCompanyAccessRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        FiscalAuthorizationService authorization = mock(FiscalAuthorizationService.class);
        FiscalAuditService audit = mock(FiscalAuditService.class);
        Usuario dono = new Usuario(); dono.setEmail("dono@empresa.test");
        Empresa empresa = new Empresa(); empresa.setDono(dono);
        when(authorization.exigir(eq(3L), eq("dono@empresa.test"), eq(FiscalPermission.GERENCIAR_ACESSOS))).thenReturn(empresa);
        Usuario colaborador = new Usuario(); colaborador.setId(9L); colaborador.setEmail("fiscal@empresa.test");
        colaborador.setEmailConfirmado(true); colaborador.setStatusAcesso(StatusAcesso.ATIVO);
        when(usuarios.findByEmail("fiscal@empresa.test")).thenReturn(Optional.of(colaborador));
        when(repository.findByEmpresaIdAndUsuarioId(3L, 9L)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> { FiscalCompanyAccess a = inv.getArgument(0); a.setId(11L); return a; });
        var service = new FiscalAccessManagementService(repository, usuarios, authorization, audit);

        var resposta = service.conceder(3L, new FiscalAccessRequest(" Fiscal@Empresa.Test ", FiscalRole.FISCAL), "dono@empresa.test");

        assertThat(resposta.role()).isEqualTo(FiscalRole.FISCAL);
        verify(audit).registrar(eq(3L), isNull(), eq("ACESSO_FISCAL_CONCEDIDO"), eq("dono@empresa.test"),
                eq("SUCESSO"), eq("usuarioId=9;papel=FISCAL"));
    }

    @Test void impedeCadastrarProprietarioComoColaborador() {
        FiscalCompanyAccessRepository repository = mock(FiscalCompanyAccessRepository.class);
        FiscalAuthorizationService authorization = mock(FiscalAuthorizationService.class);
        Empresa empresa = new Empresa(); Usuario dono = new Usuario(); dono.setEmail("dono@empresa.test"); empresa.setDono(dono);
        when(authorization.exigir(anyLong(), anyString(), any())).thenReturn(empresa);
        var service = new FiscalAccessManagementService(repository, mock(UsuarioRepository.class), authorization, mock(FiscalAuditService.class));
        assertThatThrownBy(() -> service.conceder(3L, new FiscalAccessRequest("dono@empresa.test", FiscalRole.ADMINISTRADOR), "dono@empresa.test"))
                .hasMessageContaining("proprietário");
        verifyNoInteractions(repository);
    }
}
