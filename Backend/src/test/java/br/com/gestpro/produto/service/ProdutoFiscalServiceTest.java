package br.com.gestpro.produto.service;

import br.com.gestpro.auth.model.Usuario;
import br.com.gestpro.empresa.model.Empresa;
import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.service.FiscalAuditService;
import br.com.gestpro.produto.dto.ProdutoFiscalRequest;
import br.com.gestpro.produto.model.*;
import br.com.gestpro.produto.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProdutoFiscalServiceTest {
    private ProdutoRepository produtoRepository;
    private ProdutoConfiguracaoFiscalRepository fiscalRepository;
    private ProdutoFiscalService service;
    private Produto produto;

    @BeforeEach void setup() {
        produtoRepository = mock(ProdutoRepository.class);
        fiscalRepository = mock(ProdutoConfiguracaoFiscalRepository.class);
        service = new ProdutoFiscalService(produtoRepository, fiscalRepository, mock(FiscalAuditService.class));
        Usuario dono = new Usuario(); dono.setEmail("dono@empresa.test");
        Empresa empresa = new Empresa(); empresa.setId(9L); empresa.setDono(dono); empresa.setAtivo(true);
        produto = new Produto(); produto.setId(7L); produto.setEmpresa(empresa);
        when(produtoRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(produto));
        when(fiscalRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test void criaNovaVersaoEEncerraAnteriorSemSobreporVigencia() {
        LocalDate inicioAnterior = LocalDate.now();
        ProdutoConfiguracaoFiscal anterior = config(1, inicioAnterior);
        when(fiscalRepository.findTopByProdutoIdOrderByVersaoDesc(7L)).thenReturn(Optional.of(anterior));
        var resposta = service.criarVersao(7L, request(inicioAnterior.plusDays(10)), "dono@empresa.test");
        assertThat(resposta.versao()).isEqualTo(2);
        assertThat(anterior.getVigenciaFim()).isEqualTo(inicioAnterior.plusDays(9));
        ArgumentCaptor<ProdutoConfiguracaoFiscal> captor = ArgumentCaptor.forClass(ProdutoConfiguracaoFiscal.class);
        verify(fiscalRepository).save(captor.capture());
        assertThat(captor.getValue().getEmpresaId()).isEqualTo(9L);
        assertThat(captor.getValue().isConfirmadoResponsavel()).isTrue();
    }

    @Test void bloqueiaUsuarioDeOutraEmpresaAntesDeConsultarOuGravarConfiguracao() {
        assertThatThrownBy(() -> service.criarVersao(7L, request(LocalDate.now()), "intruso@empresa.test"))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(fiscalRepository);
    }

    @Test void exigeExatamenteUmRegimeIcms() {
        ProdutoFiscalRequest request = request(LocalDate.now()); request.setCstIcms("00");
        assertThatThrownBy(() -> service.criarVersao(7L, request, "dono@empresa.test"))
                .isInstanceOf(ApiException.class).hasMessageContaining("exatamente um");
        verify(fiscalRepository, never()).save(any());
    }

    @Test void bloqueiaSobreposicaoDetectadaNaLeitura() {
        when(produtoRepository.findById(7L)).thenReturn(Optional.of(produto));
        when(fiscalRepository.vigentes(eq(7L), any())).thenReturn(List.of(config(1, LocalDate.now()), config(2, LocalDate.now())));
        assertThatThrownBy(() -> service.vigente(7L, LocalDate.now(), "dono@empresa.test"))
                .isInstanceOf(ApiException.class).hasMessageContaining("sobreposição");
    }

    private ProdutoFiscalRequest request(LocalDate inicio) {
        ProdutoFiscalRequest r = new ProdutoFiscalRequest(); r.setVigenciaInicio(inicio); r.setNcm("12345678");
        r.setOrigem("0"); r.setUnidadeComercial("UN"); r.setUnidadeTributavel("UN"); r.setCfopPadrao("5102");
        r.setCsosn("102"); r.setCstPis("07"); r.setCstCofins("07"); r.setConfirmadoResponsavel(true); return r;
    }
    private ProdutoConfiguracaoFiscal config(int versao, LocalDate inicio) {
        return new ProdutoConfiguracaoFiscal(7L, 9L, versao, inicio, "12345678", null, "0", "UN", "UN",
                null, "5102", "102", null, null, "07", "07", null, null, "dono@empresa.test");
    }
}
