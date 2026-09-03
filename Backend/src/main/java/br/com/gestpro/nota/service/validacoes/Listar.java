package br.com.gestpro.nota.service.validacoes;

import br.com.gestpro.nota.dto.FilterNotaFiscalDTO;
import br.com.gestpro.nota.model.NotaFiscal;
import br.com.gestpro.nota.repository.NotaFiscalRepository;
import br.com.gestpro.infra.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;          // ← CORRIGIDO: Spring Data
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class Listar {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final NotaFiscalRepository notaRepo;

    // Ação principal

    @Transactional(readOnly = true)
    public Map<String, Object> listar(FilterNotaFiscalDTO filter) {

        if (filter == null || filter.getEmpresaId() == null)
            throw invalido("empresaId é obrigatório.");
        int page  = filter.getPage()  != null ? filter.getPage()  : 1;
        int limit = filter.getLimit() != null ? filter.getLimit() : 20;
        if (page < 1 || page > 100_000) throw invalido("Página fora do intervalo permitido.");
        if (limit < 1 || limit > 100) throw invalido("O limite deve estar entre 1 e 100.");
        if (filter.getClienteNome() != null && filter.getClienteNome().trim().length() > 120)
            throw invalido("Filtro de cliente excessivo.");
        String chaveAcesso = normalizarChave(filter.getChaveAcesso());
        String serie = blankToNull(filter.getSerie());
        if (serie != null && !serie.matches("\\d{1,3}")) throw invalido("Série fiscal inválida.");
        if (filter.getNumero() != null && filter.getNumero() < 1) throw invalido("Número fiscal inválido.");
        if (filter.getValorMin() != null && filter.getValorMin().signum() < 0
                || filter.getValorMax() != null && filter.getValorMax().signum() < 0
                || filter.getValorMin() != null && filter.getValorMax() != null
                && filter.getValorMin().compareTo(filter.getValorMax()) > 0)
            throw invalido("Faixa de valor inválida.");

        // page - 1: Spring Data usa índice base-0; a API expõe base-1
        Pageable pageable = PageRequest.of(
                Math.max(0, page - 1),
                limit,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        LocalDateTime inicio = parseDate(filter.getDataInicio(), true);
        LocalDateTime fim    = parseDate(filter.getDataFim(), false);

        log.info("Listando notas - Empresa={}, Status={}, Tipo={}, Página={}/{}",
                filter.getEmpresaId(), filter.getStatus(), filter.getTipo(), page, limit);

        // Sem cast — Pageable já é o tipo correto (Spring Data)
        Page<NotaFiscal> resultado = notaRepo.findWithFilters(
                filter.getEmpresaId(),
                filter.getStatus(),
                filter.getTipo(),
                filter.getAmbiente(),
                blankToNull(filter.getClienteNome()),
                chaveAcesso,
                filter.getNumero(),
                serie,
                filter.getValorMin(),
                filter.getValorMax(),
                inicio,
                fim,
                pageable
        );

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("data",        resultado.getContent().stream()
                .map(this::notaResumo)
                .collect(Collectors.toList()));
        resposta.put("total",       resultado.getTotalElements());
        resposta.put("pages",       resultado.getTotalPages());
        resposta.put("page",        page);
        resposta.put("limit",       limit);
        resposta.put("hasNext",     resultado.hasNext());
        resposta.put("hasPrevious", resultado.hasPrevious());

        return resposta;
    }

    // Helpers

    /**
     * Resumo leve para a listagem — campos pesados (XMLs, paths) são omitidos.
     */
    private Map<String, Object> notaResumo(NotaFiscal n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",             n.getId());
        m.put("numeroNota",     n.getNumeroNota() != null
                ? String.format("%09d", n.getNumeroNota()) : null);
        m.put("serie",          n.getSerie());
        m.put("tipo",           n.getTipo());
        m.put("status",         n.getStatus());
        m.put("ambiente",       n.getAmbiente());
        m.put("clienteNome",    n.getClienteNome());
        m.put("clienteCpfCnpj", n.getClienteCpfCnpj());
        m.put("valorTotal",     n.getValorTotal());
        m.put("chaveAcesso",    n.getChaveAcesso());
        m.put("protocolo",      n.getProtocolo());
        m.put("dataEmissao",    n.getDataEmissao());
        m.put("createdAt",      n.getCreatedAt());
        return m;
    }

    private LocalDateTime parseDate(String s, boolean startOfDay) {
        if (s == null || s.isBlank()) return null;
        try {
            LocalDate d = LocalDate.parse(s.trim(), DATE_FMT);
            return startOfDay ? d.atStartOfDay() : d.atTime(23, 59, 59);
        } catch (DateTimeParseException e) { throw invalido("Data inválida; utilize o formato yyyy-MM-dd."); }
    }

    private String blankToNull(String s) {
        return (s != null && !s.isBlank()) ? s.trim() : null;
    }

    private String normalizarChave(String chave) {
        if (chave == null || chave.isBlank()) return null;
        String normalizada = chave.replaceAll("\\s", "");
        if (!normalizada.matches("\\d{44}"))
            throw invalido("Chave de acesso inválida; informe exatamente 44 dígitos.");
        return normalizada;
    }

    private ApiException invalido(String mensagem) {
        return new ApiException(mensagem, HttpStatus.BAD_REQUEST, "/api/nota-fiscal");
    }
}
