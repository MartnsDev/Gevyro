package br.com.gestpro.nota.service.validacoes;

import br.com.gestpro.infra.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultarCEP {

    private final WebClient webClient;

    public Map<String, Object> consultarCep(String cep) {
        String cepLimpo = cep != null ? cep.replaceAll("\\D", "") : "";

        if (cepLimpo.length() != 8) {
            throw new ApiException(
                    "CEP inválido. Informe 8 dígitos.",
                    HttpStatus.BAD_REQUEST,
                    "/api/nota-fiscal/cep"
            );
        }

        // TENTATIVA 1: ViaCEP
        try {
            return consultarViaCep(cepLimpo);
        } catch (Exception e) {
            log.warn("ViaCEP indisponível. Tentando BrasilAPI...");

            // TENTATIVA 2: BrasilAPI
            try {
                return consultarBrasilApi(cepLimpo);
            } catch (Exception e2) {
                log.warn("BrasilAPI indisponível. Tentando Postmon...");

                // TENTATIVA 3: Postmon
                try {
                    return consultarPostmon(cepLimpo);
                } catch (Exception e3) {
                    log.error("Todas as APIs de CEP falharam.");
                    throw new ApiException(
                            "Todos os serviços de consulta de CEP estão instáveis. Por favor, preencha o endereço manualmente.",
                            HttpStatus.BAD_GATEWAY,
                            "/api/nota-fiscal/cep"
                    );
                }
            }
        }
    }

    private Map<String, Object> consultarViaCep(String cep) {
        Map<?, ?> resp = webClient.get()
                .uri("https://viacep.com.br/ws/" + cep + "/json/")
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (resp == null || Boolean.TRUE.equals(resp.get("erro"))) {
            throw new RuntimeException("CEP não encontrado");
        }

        return montarResposta(
                (String) resp.get("logradouro"),
                (String) resp.get("complemento"),
                (String) resp.get("bairro"),
                (String) resp.get("localidade"),
                (String) resp.get("uf"),
                (String) resp.get("ibge"),
                cep
        );
    }

    private Map<String, Object> consultarBrasilApi(String cep) {
        Map<?, ?> resp = webClient.get()
                .uri("https://brasilapi.com.br/api/cep/v1/" + cep)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (resp == null) throw new RuntimeException("Falha na BrasilAPI");

        return montarResposta(
                (String) resp.get("street"),
                "", // BrasilAPI v1 não traz complemento por padrão
                (String) resp.get("neighborhood"),
                (String) resp.get("city"),
                (String) resp.get("state"),
                "", // IBGE não disponível na v1
                cep
        );
    }

    private Map<String, Object> consultarPostmon(String cep) {
        Map<?, ?> resp = webClient.get()
                .uri("https://api.postmon.com.br/v1/cep/" + cep)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (resp == null) throw new RuntimeException("Falha no Postmon");

        return montarResposta(
                (String) resp.get("logradouro"),
                (String) resp.get("complemento"),
                (String) resp.get("bairro"),
                (String) resp.get("cidade"),
                (String) resp.get("estado"),
                (String) ((Map<?,?>)resp.get("cidade_info")).get("codigo_ibge"),
                cep
        );
    }

    private Map<String, Object> montarResposta(String logradouro, String complemento, String bairro,
                                               String cidade, String estado, String ibge, String cep) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("cep", cep.substring(0, 5) + "-" + cep.substring(5));
        resultado.put("logradouro", logradouro);
        resultado.put("complemento", complemento);
        resultado.put("bairro", bairro);
        resultado.put("cidade", cidade);
        resultado.put("estado", estado);
        resultado.put("ibge", ibge);
        return resultado;
    }
}
