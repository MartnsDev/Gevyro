package br.com.gestpro.nota.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/** Carrega observabilidade sem depender da codificação legada dos arquivos application-*.properties. */
@Configuration
@PropertySource("classpath:fiscal-observability.properties")
public class FiscalObservabilityConfig {
}
