package br.com.gestpro.nota.service.validacoes;

import br.com.gestpro.nota.NotaFiscalStatus;
import br.com.gestpro.nota.model.NotaFiscal;
import br.com.gestpro.nota.repository.NotaFiscalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class Emitir {

    private final NotaFiscalRepository notaRepo;
    private final BuscaPorId           buscaPorId;
    private final GerarChaveAcesso     gerarChaveAcesso;

    // Ação principal

    @Transactional
    public Map<String, Object> emitir(Long id) {

        NotaFiscal nota = buscaPorId.buscarEntidade(id);

        // 1. Validações de negócio
        if (nota.getStatus() != NotaFiscalStatus.DIGITACAO
                && nota.getStatus() != NotaFiscalStatus.REJEITADA) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Somente notas em DIGITAÇÃO ou REJEITADAS podem ser (re)emitidas. "
                            + "Status atual: " + nota.getStatus().getDescricao());
        }

        if (nota.getValorTotal() == null || nota.getValorTotal().signum() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "O valor total da nota deve ser positivo para emissão.");
        }

        // 2. Geração da chave de acesso
        //    O cUF real deve vir do EmpresaService.
        //    Enquanto isso, utilizamos "35" (SP) como padrão seguro para testes.
        String cUF = "35";  // TODO: obter via empresaService.buscarPorId(nota.getEmpresaId()).getUf()
        String cnpjEmitente = "00000000000191"; // TODO: idem, buscar CNPJ real
        String chaveAcesso = gerarChaveAcesso.gerar(nota, cnpjEmitente, cUF);

        // 3. Protocolo no formato SEFAZ: "1" + cUF(2) + ano(4) + sequência(15)
        String protocolo = gerarProtocolo(cUF);

        // 4. Persistência
        nota.setStatus(NotaFiscalStatus.AUTORIZADA);
        nota.setChaveAcesso(chaveAcesso);
        nota.setProtocolo(protocolo);
        nota.setDataAutorizacao(LocalDateTime.now());
        nota.setMotivoRejeicao(null); // Limpa eventual motivo de rejeição anterior

        NotaFiscal salva = notaRepo.save(nota);
        log.info("Nota fiscal ID={} emitida com sucesso.", id);

        Map<String, Object> resposta = buscaPorId.notaParaMap(salva);
        resposta.put("mensagem", "Nota fiscal emitida com sucesso.");
        return resposta;
    }

    // Helpers

    /**
     * Gera um protocolo no formato padrão da SEFAZ:
     * {@code "1" + cUF(2) + ano(4) + sequência(15 dígitos)}.
     * Exemplo: {@code 135202400000000000001}.
     */
    private String gerarProtocolo(String cUF) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String sequencia = String.format("%015d",
                Long.parseLong(timestamp.substring(timestamp.length() - 10)));
        return "1" + cUF + LocalDate.now().getYear() + sequencia;
    }
}
