package br.com.gestpro.nota.service.validacoes;

import br.com.gestpro.nota.NotaFiscalStatus;
import br.com.gestpro.nota.dto.EstatisticasResponse;
import br.com.gestpro.nota.repository.NotaFiscalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

/**
 * Serviço responsável por calcular as estatísticas do Dashboard de notas fiscais.
 * * Retorna:
 * - Quantidade de notas por status (Autorizada, Rejeitada, Cancelada)
 * - Valor total faturado no mês corrente (soma apenas das notas AUTORIZADAS)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Estatisticas {

    private final NotaFiscalRepository notaRepo;

    // Colocamos readOnly = true porque é apenas consulta (melhora a performance no banco)
    @Transactional(readOnly = true)
    public EstatisticasResponse calcularEstatisticas(Long empresaId) {

        log.info("Calculando métricas do dashboard fiscal para a Empresa ID={}", empresaId);

        Map<NotaFiscalStatus, Long> totais = new EnumMap<>(NotaFiscalStatus.class);
        for (Object[] linha : notaRepo.countByStatus(empresaId)) {
            totais.put((NotaFiscalStatus) linha[1], ((Number) linha[0]).longValue());
        }
        long autorizadas = total(totais, NotaFiscalStatus.AUTORIZADA);
        long rejeitadas = total(totais, NotaFiscalStatus.REJEITADA);
        long canceladas = total(totais, NotaFiscalStatus.CANCELADA);
        long aguardando = total(totais, NotaFiscalStatus.PENDENTE_EMISSAO)
                + total(totais, NotaFiscalStatus.VALIDANDO)
                + total(totais, NotaFiscalStatus.PROCESSANDO);
        long erros = total(totais, NotaFiscalStatus.ERRO_TECNICO);

        // 2. Cálculo do Faturamento do mês corrente
        LocalDate hoje = LocalDate.now();
        LocalDateTime inicioMes = hoje.withDayOfMonth(1).atStartOfDay();
        LocalDateTime fimMes = hoje.withDayOfMonth(hoje.lengthOfMonth()).atTime(23, 59, 59);

        // Faturamento só contabiliza o que a SEFAZ disse "OK" (AUTORIZADA).
        BigDecimal valorMes = notaRepo.sumValorTotalByEmpresaIdAndStatusAndDataEmissaoBetween(
                empresaId, NotaFiscalStatus.AUTORIZADA, inicioMes, fimMes
        );

        if (valorMes == null) {
            valorMes = BigDecimal.ZERO;
        }

        // 3. Montando a resposta usando o DTO interno que criamos lá no NotaFiscalServiceImpl
        return EstatisticasResponse.builder()
                .totalAutorizadas(autorizadas)
                .totalRejeitadas(rejeitadas)
                .totalCanceladas(canceladas)
                .totalAguardando(aguardando)
                .totalErros(erros)
                .valorTotalMes(valorMes)
                .build();
    }

    private long total(Map<NotaFiscalStatus, Long> totais, NotaFiscalStatus status) {
        return totais.getOrDefault(status, 0L);
    }
}
