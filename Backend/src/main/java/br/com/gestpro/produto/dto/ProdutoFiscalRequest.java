package br.com.gestpro.produto.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;

@Data
public class ProdutoFiscalRequest {
    @NotNull private LocalDate vigenciaInicio;
    @NotBlank @Pattern(regexp="\\d{8}") private String ncm;
    @Pattern(regexp="^$|\\d{7}") private String cest;
    @NotBlank @Pattern(regexp="[0-8]") private String origem;
    @NotBlank @Pattern(regexp="[A-Z0-9]{1,6}") private String unidadeComercial;
    @NotBlank @Pattern(regexp="[A-Z0-9]{1,6}") private String unidadeTributavel;
    @Pattern(regexp="^$|\\d{8}|\\d{12,14}") private String gtin;
    @NotBlank @Pattern(regexp="\\d{4}") private String cfopPadrao;
    @Pattern(regexp="^$|\\d{3}") private String csosn;
    @Pattern(regexp="^$|\\d{2,3}") private String cstIcms;
    @Pattern(regexp="^$|\\d{2}") private String cstIpi;
    @NotBlank @Pattern(regexp="\\d{2}") private String cstPis;
    @NotBlank @Pattern(regexp="\\d{2}") private String cstCofins;
    @Pattern(regexp="^$|\\d{3}") private String cstIbsCbs;
    @Pattern(regexp="^$|\\d{6}") private String cclassTrib;
    @AssertTrue(message="A classificação fiscal deve ser confirmada pelo responsável fiscal ou contador")
    private boolean confirmadoResponsavel;
}
