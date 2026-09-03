package br.com.gestpro.nota.service;

import br.com.gestpro.auth.model.Usuario;
import br.com.gestpro.empresa.model.Empresa;
import br.com.gestpro.empresa.repository.EmpresaRepository;
import br.com.gestpro.nota.*;
import br.com.gestpro.nota.model.FiscalCompanyAccess;
import br.com.gestpro.nota.repository.FiscalCompanyAccessRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FiscalAuthorizationServiceTest {
    @Test void negaOperacaoForaDoPapelELiberaProprietario() {
        EmpresaRepository empresas = mock(EmpresaRepository.class);
        FiscalCompanyAccessRepository acessos = mock(FiscalCompanyAccessRepository.class);
        Usuario dono = new Usuario(); dono.setEmail("dono@empresa.test");
        Empresa empresa = new Empresa(); empresa.setId(3L); empresa.setDono(dono); empresa.setAtivo(true);
        when(empresas.findByIdWithDono(3L)).thenReturn(Optional.of(empresa));
        when(acessos.findActive(3L, "leitor@empresa.test")).thenReturn(Optional.of(
                FiscalCompanyAccess.builder().role(FiscalRole.SOMENTE_LEITURA).ativo(true).build()));
        FiscalAuthorizationService service = new FiscalAuthorizationService(empresas, acessos);

        assertThat(service.exigir(3L, "dono@empresa.test", FiscalPermission.GERENCIAR_CERTIFICADO)).isSameAs(empresa);
        assertThatThrownBy(() -> service.exigir(3L, "leitor@empresa.test", FiscalPermission.EMITIR))
                .hasMessageContaining("Sem permissão");
        assertThat(service.exigir(3L, "leitor@empresa.test", FiscalPermission.VISUALIZAR)).isSameAs(empresa);
    }
}
