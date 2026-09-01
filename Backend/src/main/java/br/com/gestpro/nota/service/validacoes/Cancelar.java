package br.com.gestpro.nota.service.validacoes;

import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.nota.NotaFiscalStatus;
import br.com.gestpro.nota.dto.CancelarNotaRequest;
import br.com.gestpro.nota.model.NotaFiscal;
import br.com.gestpro.nota.repository.NotaFiscalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Serviço responsável por processar o cancelamento interno de uma nota fiscal.
 *
 * Regras de negócio:
 * - Somente notas AUTORIZADAS podem ser canceladas.
 * - Rascunhos (Em Digitação) devem ser apenas excluídos.
 * - A justificativa deve ter no mínimo 15 caracteres (Exigência SEFAZ).
 */
@Slf4j
@Service
@RequiredArgsConstructor // <-- Lombok injetando os repositórios
public class Cancelar {

    private final NotaFiscalRepository notaRepo;
    private final BuscaPorId buscaPorId;

    public Map<String, Object> cancelar(CancelarNotaRequest request) {

        // Nota: Assumi que getNotaId() é o método do seu novo CancelarNotaRequest
        NotaFiscal nota = buscaPorId.buscarEntidade(request.getNotaId());

        // 1. Validação de status
        if (nota.getStatus() == NotaFiscalStatus.CANCELADA) {
            log.warn("Tentativa de cancelar nota já cancelada: ID={}", nota.getId());
            throw new ApiException(
                    "Esta nota já está cancelada.",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "/api/nota-fiscal/cancelar"
            );
        }

        if (nota.getStatus() == NotaFiscalStatus.DIGITACAO) {
            log.warn("Tentativa de cancelar rascunho: ID={}", nota.getId());
            throw new ApiException(
                    "Notas em digitação não precisam ser canceladas – exclua a nota diretamente na lixeira.",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "/api/nota-fiscal/cancelar"
            );
        }

        if (nota.getStatus() != NotaFiscalStatus.AUTORIZADA) {
            throw new ApiException(
                    "Somente notas AUTORIZADAS pela SEFAZ podem ser canceladas.",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "/api/nota-fiscal/cancelar"
            );
        }

        // 2. Validação do motivo (SEFAZ exige 15 a 256 caracteres)
        String motivo = request.getJustificativa();
        if (motivo == null || motivo.isBlank() || motivo.trim().length() < 15 || motivo.trim().length() > 255) {
            throw new ApiException(
                    "A justificativa do cancelamento deve ter entre 15 e 255 caracteres.",
                    HttpStatus.BAD_REQUEST,
                    "/api/nota-fiscal/cancelar"
            );
        }

        // 3. Executar cancelamento no banco de dados
        // OBS: O envio do evento XML para a SEFAZ deve ser chamado a partir daqui ou no NotaFiscalServiceImpl
        log.info("Marcando Nota Fiscal ID={} como CANCELADA.", nota.getId());

        nota.setStatus(NotaFiscalStatus.CANCELADA);

        // Salvamos a justificativa no motivo de rejeição/histórico para manter rastreabilidade
        nota.setMotivoRejeicao("Cancelamento justificado: " + motivo.trim());

        NotaFiscal salva = notaRepo.save(nota);

        Map<String, Object> resposta = buscaPorId.notaParaMap(salva);
        resposta.put("mensagem", "Nota fiscal cancelada com sucesso.");
        return resposta;
    }
}
