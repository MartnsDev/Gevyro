package br.com.gestpro.nota.service;

import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.empresa.model.Empresa;
import br.com.gestpro.empresa.repository.EmpresaRepository;
import br.com.gestpro.nota.NotaFiscalStatus;
import br.com.gestpro.nota.RegimeTributario;
import br.com.gestpro.nota.config.NotaFiscalConfig;
import br.com.gestpro.nota.dto.*;
import br.com.gestpro.nota.model.*;
import br.com.gestpro.nota.repository.ItemNotaFiscalRepository;
import br.com.gestpro.nota.repository.NotaFiscalRepository;
import br.com.gestpro.nota.repository.ConfiguracaoFiscalEmpresaRepository;
import br.com.gestpro.nota.provider.FiscalProvider;
import br.com.gestpro.nota.provider.FiscalProviderRegistry;
import br.com.gestpro.nota.service.validacoes.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.*;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class NotaFiscalServiceImpl implements NotaFiscalInterface {

    private final NotaFiscalRepository notaFiscalRepository;
    private final ItemNotaFiscalRepository itemRepository;
    private final GerarChaveAcesso gerarChaveAcesso;
    private final XmlGeneratorService xmlGeneratorService;
    private final AssinaturaDigitalService assinaturaService;
    private final FiscalProviderRegistry fiscalProviderRegistry;
    private final NotaFiscalConfig.NotaFiscalProperties config;
    private final Criar criarService; // Serviço que já refatoramos para calcular totais
    private final Cancelar cancelarService; // Serviço que refatoramos para validar cancelamento
    private final ConsultarCEP consultarCepService;
    private final ConsultarCNPJ consultarCnpjService;
    private final BuscarMunicipios buscarMunicipiosService;
    private final EmpresaRepository empresaRepository;
    private final CertificateService certificateService;
    private final FiscalIdempotencyService idempotencyService;
    private final FiscalAuditService auditService;
    private final ConfiguracaoFiscalEmpresaRepository configuracaoFiscalRepository;
    private final FiscalXmlService fiscalXmlService;
    private final FiscalXsdValidationService xsdValidationService;
    private final DanfePdfService danfePdfService;

    @Transactional(readOnly = true)
    public void validarAcessoEmpresa(Long empresaId, String emailUsuario) {
        if (empresaId == null) throw new ApiException("empresaId é obrigatório.", HttpStatus.BAD_REQUEST, "/api/nota-fiscal");
        Empresa empresa=empresaRepository.findByIdWithDono(empresaId)
                .orElseThrow(()->new ApiException("Empresa não encontrada.",HttpStatus.NOT_FOUND,"/api/nota-fiscal"));
        if (!empresa.getDono().getEmail().equals(emailUsuario))
            throw new ApiException("Sem permissão para acessar os dados fiscais desta empresa.",HttpStatus.FORBIDDEN,"/api/nota-fiscal");
        if (!Boolean.TRUE.equals(empresa.getAtivo()))
            throw new ApiException("Esta empresa está arquivada.",HttpStatus.CONFLICT,"/api/nota-fiscal");
    }

    @Transactional(readOnly = true)
    public void validarAcessoNota(Long notaId,String emailUsuario){
        NotaFiscal nota=buscarPorId(notaId);validarAcessoEmpresa(nota.getEmpresaId(),emailUsuario);
    }

    // CRUD BÁSICO

    @Override
    public NotaFiscal criar(CriarNotaRequest request) {
        // Delega para a classe 'Criar' que tem toda a lógica complexa de matemática tributária
        Map<String, Object> mapNota = criarService.criar(request);
        // O map retorna a nota persistida. Buscamos do banco para devolver a entidade.
        // O ideal seria que 'Criar' retornasse a Entidade e não o Map, mas para adaptar:
        Long notaId = (Long) ((Map<String, Object>) mapNota.get("nota")).get("id");
        return buscarPorId(notaId);
    }

    @Override
    @Transactional(readOnly = true)
    public NotaFiscal buscarPorId(Long id) {
        return notaFiscalRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        "Nota fiscal não encontrada com o ID: " + id,
                        HttpStatus.NOT_FOUND,
                        "/api/nota-fiscal"
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotaFiscalResumoResponse> listar(Long empresaId, NotaFiscalStatus status) {
        List<NotaFiscal> notas = status != null
                ? notaFiscalRepository.findByEmpresaIdAndStatusOrderByDataEmissaoDesc(empresaId, status)
                : notaFiscalRepository.findByEmpresaIdOrderByDataEmissaoDesc(empresaId);

        return notas.stream().map(this::toResumo).collect(Collectors.toList());
    }

    @Override
    public void excluir(Long id) {
        NotaFiscal nota = buscarPorId(id);
        if (nota.getStatus() != NotaFiscalStatus.DIGITACAO && nota.getStatus() != NotaFiscalStatus.REJEITADA) {
            throw new ApiException(
                    "Apenas rascunhos (em digitação) ou notas rejeitadas podem ser excluídas. Se a nota foi autorizada, use o Cancelamento.",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "/api/nota-fiscal"
            );
        }
        log.info("Excluindo rascunho de nota fiscal ID={}", id);
        notaFiscalRepository.delete(nota);
    }

    // O CORAÇÃO: EMISSÃO PARA A SEFAZ

    public NotaFiscal emitirIdempotente(Long notaId, String idempotencyKey, String ator) {
        NotaFiscal atual = buscarPorId(notaId);
        FiscalIdempotencyService.Resultado operacao = idempotencyService
                .iniciarEmissao(atual.getEmpresaId(), notaId, idempotencyKey);
        if (operacao.concluida()) return atual;
        if (operacao.resultadoDesconhecido()) {
            throw new ApiException("O resultado da emissão anterior é desconhecido. Consulte a situação da nota antes de reenviar.",
                    HttpStatus.CONFLICT, "/api/nota-fiscal/emitir");
        }
        if (operacao.falhou()) {
            throw new ApiException("A tentativa anterior falhou. Corrija a nota e use uma nova Idempotency-Key.",
                    HttpStatus.UNPROCESSABLE_ENTITY, "/api/nota-fiscal/emitir");
        }
        if (!operacao.nova()) {
            throw new ApiException("Esta emissão já está em processamento.", HttpStatus.CONFLICT, "/api/nota-fiscal/emitir");
        }
        auditService.registrar(atual.getEmpresaId(), notaId, "EMISSAO_SOLICITADA", ator, "ACEITA", null);
        try {
            NotaFiscal emitida = emitir(notaId);
            idempotencyService.concluir(operacao.operacaoId());
            auditService.registrar(atual.getEmpresaId(), notaId, "EMISSAO_FINALIZADA", ator,
                    emitida.getStatus().name(), emitida.getProtocolo() == null ? null : "protocolo=" + emitida.getProtocolo());
            return emitida;
        } catch (RuntimeException erro) {
            idempotencyService.resultadoDesconhecido(operacao.operacaoId());
            auditService.registrar(atual.getEmpresaId(), notaId, "EMISSAO_FALHOU", ator, "RESULTADO_DESCONHECIDO", erro.getClass().getSimpleName());
            throw erro;
        }
    }

    @Override
    public NotaFiscal emitir(Long notaId) {
        // 1. Busca a nota e valida o status
        NotaFiscal nota = notaFiscalRepository.findByIdForUpdate(notaId)
                .orElseThrow(() -> new ApiException("Nota fiscal não encontrada.", HttpStatus.NOT_FOUND, "/api/nota-fiscal/emitir"));

        if (nota.getStatus() == NotaFiscalStatus.AUTORIZADA) {
            throw new ApiException(
                    "Esta nota já está autorizada na base de dados da SEFAZ.",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "/api/nota-fiscal/emitir"
            );
        }

        if (nota.getStatus() == NotaFiscalStatus.CANCELADA) {
            throw new ApiException(
                    "Não é possível emitir uma nota que foi cancelada.",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "/api/nota-fiscal/emitir"
            );
        }

        if (nota.getStatus() == NotaFiscalStatus.VALIDANDO || nota.getStatus() == NotaFiscalStatus.PROCESSANDO) {
            throw new ApiException("Esta nota já possui uma emissão em andamento.", HttpStatus.CONFLICT, "/api/nota-fiscal/emitir");
        }

        // 2. Busca dados complementares
        // TODO: Injetar o Serviço de Empresa real aqui para pegar os dados completos do emitente
        if (nota.getTipo()==br.com.gestpro.nota.TipoNota.NFSE)
            throw new ApiException("NFS-e depende da integração municipal e ainda não está disponível para transmissão.",HttpStatus.NOT_IMPLEMENTED,"/api/nota-fiscal/emitir");
        EmpresaInfo empresa = buscarEmpresaInfo(nota.getEmpresaId());
        List<ItemNotaFiscal> itens = itemRepository.findByNotaFiscalId(nota.getId());

        if (itens.isEmpty()) {
            throw new ApiException("Não é possível emitir uma nota sem produtos/serviços.", HttpStatus.BAD_REQUEST, "/api/nota-fiscal/emitir");
        }

        try (CertificateService.Material material = certificateService.carregar(nota.getEmpresaId())) {
            byte[] certBytes = material.arquivo();
            String senhaCert = material.senha();
            // Marca no banco que o processo de envio começou (impede concorrência)
            nota.setStatus(NotaFiscalStatus.VALIDANDO);
            notaFiscalRepository.save(nota);

            // 3. Geração da Chave de Acesso (44 posições)
            String chaveFinal = gerarChaveAcesso.gerar(nota, empresa.getCnpj(),
                    GerarChaveAcesso.getCodigoUf(empresa.getUf()));
            nota.setChaveAcesso(chaveFinal);

            // 4. Montagem do XML (Assinatura Nua)
            log.info("Iniciando geração do XML da NF-e ID={}", notaId);
            boolean homologacao = isHomologacao(nota.getEmpresaId());
            String xmlBruto = xmlGeneratorService.gerarXmlNfe(nota, empresa, itens, chaveFinal, homologacao);

            // 5. Assinatura Digital do XML
            log.info("Assinando digitalmente o XML com o certificado da empresa...");
            String xmlAssinado = assinaturaService.assinarXml(xmlBruto, certBytes, senhaCert);
            xsdValidationService.validarNfeAssinada(xmlAssinado);
            nota.setXmlEnviado(xmlAssinado);
            nota.setStatus(NotaFiscalStatus.PROCESSANDO);
            notaFiscalRepository.save(nota);

            // Contingência só pode ser ativada por regra fiscal explícita; uma
            // indisponibilidade genérica de internet não define modalidade fiscal.
            log.info("Enviando XML assinado para a SEFAZ do estado: {}", empresa.getUf());
            FiscalProvider provider = fiscalProviderRegistry.oficialPara(nota.getTipo());
            FiscalProvider.AutorizacaoResultado resultado = provider.autorizar(new FiscalProvider.AutorizacaoComando(
                    xmlAssinado, empresa.getUf(), nota.getTipo(), homologacao, certBytes, senhaCert));
            SefazComunicacaoService.RetornoSefaz retorno = toRetornoLegado(resultado);

            // 8. Tratamento da Resposta da SEFAZ
            return processarRetornoSefaz(nota, retorno, xmlAssinado);

        } catch (ApiException e) {
            // Repassa erros de negócio que nós mesmos lançamos
            nota.setStatus(NotaFiscalStatus.ERRO_TECNICO);
            nota.setMotivoRejeicao("Falha técnica durante a emissão. Consulte os detalhes da operação antes de tentar novamente.");
            notaFiscalRepository.save(nota);
            throw e;

        } catch (Exception e) {
            log.error("Falha catastrófica ao tentar emitir NF-e ID={}", notaId, e);

            // Marca a nota como rejeitada por falha técnica
            nota.setStatus(NotaFiscalStatus.ERRO_TECNICO);
            nota.setMotivoRejeicao("Falha técnica ou resultado desconhecido na comunicação fiscal.");
            notaFiscalRepository.save(nota);

            throw new ApiException(
                    "Não foi possível confirmar o resultado da emissão. Consulte a situação da nota antes de tentar novamente.",
                    HttpStatus.BAD_GATEWAY,
                    "/api/nota-fiscal/emitir"
            );
        }
    }

    // OUTRAS AÇÕES DE CICLO DE VIDA E AUXILIARES

    @Override
    public NotaFiscal cancelar(CancelarNotaRequest request) {
        NotaFiscal nota = buscarPorId(request.getNotaId());
        EmpresaInfo empresa = buscarEmpresaInfo(nota.getEmpresaId());

        // Valida o pedido e registra o cancelamento na mesma transação. Se a
        // SEFAZ falhar, a exceção abaixo desfaz a alteração no banco.
        Map<String, Object> mapNotaCancelada = cancelarService.cancelar(request);
        Long notaIdCancelada = (Long) ((Map<String, Object>) mapNotaCancelada.get("nota")).get("id");
        nota = buscarPorId(notaIdCancelada);

        try (CertificateService.Material material = certificateService.carregar(nota.getEmpresaId())) {
            byte[] certBytes = material.arquivo();
            String senhaCert = material.senha();
            // Dispara o evento XML para a SEFAZ avisando do cancelamento
            FiscalProvider provider = fiscalProviderRegistry.oficialPara(nota.getTipo());
            FiscalProvider.EventoResultado evento = provider.cancelar(new FiscalProvider.CancelamentoComando(
                    nota.getChaveAcesso(), nota.getProtocolo(), request.getJustificativa().trim(), empresa.getUf(),
                    isHomologacao(nota.getEmpresaId()), certBytes, senhaCert));
            SefazComunicacaoService.RetornoSefaz retorno = toRetornoLegado(evento);

            if (!retorno.isSucesso()) {
                throw new ApiException("A SEFAZ rejeitou o cancelamento: " + retorno.getMensagem(), HttpStatus.BAD_REQUEST, "/api/nota-fiscal/cancelar");
            }

            return nota;

        } catch (ApiException e) {
            throw e;
        } catch (br.com.gestpro.nota.provider.FiscalProviderException e) {
            log.error("Erro na comunicação do cancelamento", e);
            throw new ApiException("Falha ao comunicar cancelamento com a Fazenda Estadual.", HttpStatus.BAD_GATEWAY, "/api/nota-fiscal/cancelar");
        } catch (Exception e) {
            log.error("Falha interna ao processar cancelamento fiscal", e);
            throw new ApiException("Não foi possível processar o cancelamento fiscal.", HttpStatus.INTERNAL_SERVER_ERROR, "/api/nota-fiscal/cancelar");
        }
    }

    @Override
    public void inutilizar(InutilizarRequest request) {
        if (request.getTipo() == null || request.getTipo() == br.com.gestpro.nota.TipoNota.NFSE)
            throw new ApiException("A inutilização 4.00 é permitida somente para NF-e ou NFC-e.", HttpStatus.BAD_REQUEST, "/api/nota-fiscal/inutilizar");
        int serie;
        try { serie = Integer.parseInt(request.getSerie()); }
        catch (Exception e) { throw new ApiException("Série fiscal inválida.", HttpStatus.BAD_REQUEST, "/api/nota-fiscal/inutilizar"); }
        if (request.getNumeroInicio() == null || request.getNumeroFim() == null
                || request.getNumeroFim() < request.getNumeroInicio())
            throw new ApiException("Faixa de numeração inválida.", HttpStatus.BAD_REQUEST, "/api/nota-fiscal/inutilizar");
        if (request.getAno() == null || request.getAno() > java.time.Year.now().getValue())
            throw new ApiException("Ano fiscal inválido.", HttpStatus.BAD_REQUEST, "/api/nota-fiscal/inutilizar");
        String serieNormalizada = String.valueOf(serie);
        if (notaFiscalRepository.existsByEmpresaIdAndTipoAndSerieAndNumeroNotaBetween(request.getEmpresaId(),
                request.getTipo(), serieNormalizada, request.getNumeroInicio(), request.getNumeroFim()))
            throw new ApiException("A faixa contém número já reservado ou utilizado e não pode ser inutilizada.",
                    HttpStatus.CONFLICT, "/api/nota-fiscal/inutilizar");

        Empresa empresa = empresaRepository.findById(request.getEmpresaId())
                .orElseThrow(() -> new ApiException("Empresa não encontrada.", HttpStatus.NOT_FOUND, "/api/nota-fiscal/inutilizar"));
        String cnpj = empresa.getCnpj() == null ? "" : empresa.getCnpj().replaceAll("\\D", "");
        if (!cnpj.matches("[0-9]{14}") || empresa.getUf() == null || empresa.getUf().isBlank())
            throw new ApiException("CNPJ e UF fiscais válidos são obrigatórios para inutilização.",
                    HttpStatus.PRECONDITION_FAILED, "/api/nota-fiscal/inutilizar");

        try (CertificateService.Material material = certificateService.carregar(request.getEmpresaId())) {
            FiscalProvider provider = fiscalProviderRegistry.oficialPara(request.getTipo());
            FiscalProvider.EventoResultado resultado = provider.inutilizar(new FiscalProvider.InutilizacaoComando(
                    cnpj, empresa.getUf(), request.getTipo(), request.getAno(), serie,
                    request.getNumeroInicio(), request.getNumeroFim(), request.getJustificativa().trim(),
                    isHomologacao(request.getEmpresaId()), material.arquivo(), material.senha()));
            if (!resultado.aceito())
                throw new ApiException("A SEFAZ rejeitou a inutilização: [" + resultado.codigo() + "] " + resultado.motivo(),
                        HttpStatus.UNPROCESSABLE_ENTITY, "/api/nota-fiscal/inutilizar");
            log.info("Inutilização fiscal aceita: empresa={} tipo={} série={} faixa={}-{} protocolo={}",
                    request.getEmpresaId(), request.getTipo(), serie, request.getNumeroInicio(), request.getNumeroFim(), resultado.protocolo());
        } catch (ApiException e) {
            throw e;
        } catch (br.com.gestpro.nota.provider.FiscalProviderException e) {
            throw new ApiException("Não foi possível confirmar a inutilização. Consulte a SEFAZ antes de repetir.",
                    HttpStatus.BAD_GATEWAY, "/api/nota-fiscal/inutilizar");
        }
    }

    @Override
    public int transmitirContingencias(Long empresaId) {
        List<NotaFiscal> contingencias = notaFiscalRepository
                .findByEmpresaIdAndEmContingenciaAndStatus(empresaId, true, NotaFiscalStatus.CONTINGENCIA);

        int transmitidas = 0;
        for (NotaFiscal nota : contingencias) {
            try {
                emitir(nota.getId());
                transmitidas++;
            } catch (Exception e) {
                log.warn("Falha ao tentar retransmitir contingência ID={}: {}", nota.getId(), e.getMessage());
            }
        }
        log.info("Rotina de contingências finalizada. Transmitidas: {}/{}", transmitidas, contingencias.size());
        return transmitidas;
    }

    // EXPORTAÇÕES E CONSULTAS

    @Override
    @Transactional(readOnly = true)
    public NotaFiscal buscarPorChaveAcesso(String chaveAcesso) {
        return notaFiscalRepository.findByChaveAcesso(chaveAcesso)
                .orElseThrow(() -> new ApiException("Nota fiscal não encontrada com a chave: " + chaveAcesso, HttpStatus.NOT_FOUND, "/api/nota-fiscal"));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] baixarXml(Long notaId) {
        NotaFiscal nota = buscarPorId(notaId);
        if (nota.getStatus() == NotaFiscalStatus.AUTORIZADA || nota.getStatus() == NotaFiscalStatus.CANCELADA) {
            try { return fiscalXmlService.carregarAutorizado(notaId); }
            catch (IllegalStateException legadoSemArquivoSeguro) {
                if (nota.getXmlAutorizado() != null) return nota.getXmlAutorizado().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                throw legadoSemArquivoSeguro;
            }
        }
        String xml = nota.getXmlAutorizado() != null ? nota.getXmlAutorizado() : nota.getXmlEnviado();

        if (xml == null) {
            throw new ApiException("XML ainda não foi gerado ou está indisponível para download.", HttpStatus.NOT_FOUND, "/api/nota-fiscal/xml");
        }
        return xml.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] gerarDanfePdf(Long notaId) {
        return danfePdfService.gerar(notaId);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] gerarZipXmlsMensal(Long empresaId, YearMonth periodo) {
        LocalDateTime inicio = periodo.atDay(1).atStartOfDay();
        LocalDateTime fim = periodo.atEndOfMonth().atTime(23, 59, 59);

        List<NotaFiscal> notas = notaFiscalRepository.findByEmpresaIdAndPeriodo(empresaId, inicio, fim);

        if (notas.isEmpty()) {
            throw new ApiException("Nenhuma nota fiscal encontrada no período de " + periodo, HttpStatus.NOT_FOUND, "/api/nota-fiscal/exportar");
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            int arquivosIncluidos = 0;
            for (NotaFiscal nota : notas) {
                byte[] xmlBytes;
                try { xmlBytes = baixarXml(nota.getId()); } catch (RuntimeException indisponivel) { continue; }

                String nomeArquivo = String.format("%s-%s.xml",
                        nota.getStatus().name().toLowerCase(),
                        nota.getChaveAcesso() != null ? nota.getChaveAcesso() : nota.getId().toString()
                );

                ZipEntry entry = new ZipEntry(nomeArquivo);
                zos.putNextEntry(entry);
                zos.write(xmlBytes);
                zos.closeEntry();
                arquivosIncluidos++;
            }

            if (arquivosIncluidos == 0) {
                throw new ApiException("Nenhuma nota do período possui XML disponível para exportação.", HttpStatus.NOT_FOUND, "/api/nota-fiscal/exportar");
            }

            zos.finish();
            return baos.toByteArray();

        } catch (IOException e) {
            throw new ApiException("Erro de I/O ao zipar os arquivos XML.", HttpStatus.INTERNAL_SERVER_ERROR, "/api/nota-fiscal/exportar");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] gerarArquivoSped(Long empresaId, YearMonth periodo, String tipoSped) {
        throw new UnsupportedOperationException("Geração de arquivo SPED ainda não implementada.");
    }

    @Override
    @Transactional(readOnly = true)
    public EstatisticasResponse getEstatisticas(Long empresaId) {
        long autorizadas=notaFiscalRepository.countByEmpresaIdAndStatus(empresaId,NotaFiscalStatus.AUTORIZADA);
        long rejeitadas=notaFiscalRepository.countByEmpresaIdAndStatus(empresaId,NotaFiscalStatus.REJEITADA);
        long canceladas=notaFiscalRepository.countByEmpresaIdAndStatus(empresaId,NotaFiscalStatus.CANCELADA);
        LocalDateTime inicio=YearMonth.now().atDay(1).atStartOfDay();
        java.math.BigDecimal valor=notaFiscalRepository.sumValorAutorizadasByPeriodo(empresaId,inicio).orElse(java.math.BigDecimal.ZERO);
        return EstatisticasResponse.builder().totalAutorizadas(autorizadas).totalRejeitadas(rejeitadas).totalCanceladas(canceladas).valorTotalMes(valor).build();
    }

    @Override
    public Object consultarCep(String cep) {
        return consultarCepService.consultarCep(cep);
    }

    @Override
    public Object consultarCnpj(String cnpj) {
        return consultarCnpjService.consultarCnpj(cnpj);
    }

    @Override
    public List<Object> buscarMunicipios(String uf) {
        return new ArrayList<>(buscarMunicipiosService.buscarMunicipios(uf));
    }

    // MÉTODOS PRIVADOS - REGRAS INTERNAS

    public Map<String, String> registrarCertificado(Long empresaId, byte[] pfxBytes, String senha) {
        Map<String, String> resumo = certificateService.salvar(empresaId, pfxBytes, senha);
        log.info("Certificado digital A1 substituído com armazenamento criptografado para empresaId={}", empresaId);
        return resumo;
    }

    private NotaFiscal processarRetornoSefaz(NotaFiscal nota,
                                             SefazComunicacaoService.RetornoSefaz retorno,
                                             String xmlAssinado) {
        nota.setXmlRetorno(retorno.getXmlRetorno());

        if (retorno.isSucesso()) {
            nota.setStatus(NotaFiscalStatus.AUTORIZADA);
            nota.setProtocolo(retorno.getProtocolo());
            nota.setDataAutorizacao(LocalDateTime.now());
            nota.setMotivoRejeicao(null);

            String nfeProc = fiscalXmlService.montarNfeProc(xmlAssinado, retorno.getXmlRetorno());
            fiscalXmlService.armazenarAutorizado(nota.getEmpresaId(), nota.getId(), nfeProc);
            // A cópia legada permanece vazia para novos documentos: o original
            // cifrado e verificado é a única fonte de verdade.
            nota.setXmlAutorizado(null);

            log.info("NF-e ID={} autorizada pela SEFAZ.", nota.getId());
        } else {
            nota.setStatus(NotaFiscalStatus.REJEITADA);
            nota.setMotivoRejeicao("[" + retorno.getCodigo() + "] " + retorno.getMensagem());
            log.warn("REJEIÇÃO SEFAZ! Código: {} - Motivo: {}", retorno.getCodigo(), retorno.getMensagem());
        }

        return notaFiscalRepository.save(nota);
    }

    private NotaFiscal processarContingencia(NotaFiscal nota, String xmlAssinado) {
        nota.setEmContingencia(true);
        nota.setStatus(NotaFiscalStatus.CONTINGENCIA);
        nota.setJustificativaContingencia("Queda de conectividade de rede com a SEFAZ.");
        nota.setXmlEnviado(xmlAssinado); // Salva o XML pra transmitir depois
        log.info("NF-e salva em modo de CONTINGÊNCIA: ID={}. Ela deve ser transmitida em até 24h.", nota.getId());
        return notaFiscalRepository.save(nota);
    }

    private SefazComunicacaoService.RetornoSefaz toRetornoLegado(FiscalProvider.AutorizacaoResultado r) {
        SefazComunicacaoService.RetornoSefaz retorno = new SefazComunicacaoService.RetornoSefaz();
        retorno.setSucesso(r.autorizada()); retorno.setCodigo(r.codigo()); retorno.setMensagem(r.motivo());
        retorno.setProtocolo(r.protocolo()); retorno.setDataHoraRecebimento(r.dataRecebimento()); retorno.setXmlRetorno(r.xmlRetorno());
        return retorno;
    }
    private SefazComunicacaoService.RetornoSefaz toRetornoLegado(FiscalProvider.EventoResultado r) {
        SefazComunicacaoService.RetornoSefaz retorno = new SefazComunicacaoService.RetornoSefaz();
        retorno.setSucesso(r.aceito()); retorno.setCodigo(r.codigo()); retorno.setMensagem(r.motivo());
        retorno.setProtocolo(r.protocolo()); retorno.setXmlRetorno(r.xmlRetorno()); return retorno;
    }

    private EmpresaInfo buscarEmpresaInfo(Long empresaId) {
        Empresa e=empresaRepository.findByIdWithDono(empresaId)
                .orElseThrow(()->new ApiException("Empresa não encontrada.",HttpStatus.NOT_FOUND,"/api/nota-fiscal"));
        String cnpj=e.getCnpj()!=null?e.getCnpj().replaceAll("\\D",""):"";
        if(cnpj.length()!=14)throw new ApiException("Cadastre um CNPJ válido na empresa antes de emitir.",HttpStatus.PRECONDITION_FAILED,"/api/nota-fiscal/emitir");
        Map<String,Object> dados;
        try{dados=consultarCnpjService.consultarCnpj(cnpj);}catch(Exception ex){dados=Map.of();}
        String codigoIbge=valor(dados,"codigoIbge");
        String municipio=primeiro(e.getCidade(),valor(dados,"municipio"));
        String uf=primeiro(e.getUf(),valor(dados,"uf"));
        String razao=primeiro(e.getRazaoSocial(),valor(dados,"nome"),e.getNomeFantasia());
        if(vazio(uf)||vazio(municipio)||vazio(e.getLogradouro())||vazio(e.getBairro())||vazio(e.getCep())||vazio(codigoIbge))
            throw new ApiException("Complete o endereço fiscal da empresa e o código IBGE do município antes de emitir.",HttpStatus.PRECONDITION_FAILED,"/api/nota-fiscal/emitir");
        ConfiguracaoFiscalEmpresa fiscal = configuracaoFiscalRepository.findByEmpresaId(empresaId).orElse(null);
        return EmpresaInfo.builder().id(e.getId()).cnpj(cnpj).razaoSocial(razao).nomeFantasia(e.getNomeFantasia())
                .logradouro(e.getLogradouro()).numero(primeiro(e.getNumero(),"S/N")).bairro(e.getBairro()).municipio(municipio)
                .codigoIbge(codigoIbge).uf(uf).cep(e.getCep()).telefone(e.getTelefone())
                .inscricaoEstadual(fiscal == null ? null : fiscal.getInscricaoEstadual())
                .regimeTributario(fiscal == null ? RegimeTributario.SIMPLES_NACIONAL : fiscal.getRegimeTributario()).build();
    }

    private boolean isHomologacao(Long empresaId) {
        return configuracaoFiscalRepository.findByEmpresaId(empresaId)
                .map(c -> c.getAmbiente() == ConfiguracaoFiscalEmpresa.Ambiente.HOMOLOGACAO)
                .orElse(config.isHomologacao());
    }

    private String valor(Map<String,Object> dados,String chave){Object v=dados.get(chave);return v==null?null:String.valueOf(v);}
    private boolean vazio(String v){return v==null||v.isBlank();}
    private String primeiro(String... valores){for(String v:valores)if(!vazio(v))return v;return null;}

    public NotaFiscalResumoResponse toResumo(NotaFiscal nota) {
        return NotaFiscalResumoResponse.builder()
                .id(nota.getId())
                .numeroNota(nota.getNumeroNota() != null ? String.format("%09d", nota.getNumeroNota()) : "")
                .serie(nota.getSerie())
                .tipo(nota.getTipo())
                .status(nota.getStatus())
                .clienteNome(nota.getClienteNome())
                .chaveAcesso(nota.getChaveAcesso())
                .valorTotal(nota.getValorTotal())
                .dataEmissao(nota.getDataEmissao())
                .protocolo(nota.getProtocolo())
                .build();
    }

}
