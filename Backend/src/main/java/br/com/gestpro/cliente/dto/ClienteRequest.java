package br.com.gestpro.cliente.dto;

import lombok.Data;
import jakarta.validation.constraints.*;

@Data
public class ClienteRequest {
    @Size(max = 255)
    private String nome;
    @Email @Size(max = 254)
    private String email;
    @Size(max = 30)
    private String telefone;
    @Size(max = 14)
    private String cpf;
    @Size(max = 18)
    private String cnpj;
    @Size(max = 255)
    private String contato;
    @Size(max = 1000)
    private String observacoes;
    @Pattern(regexp = "(?i)CLIENTE|FORNECEDOR")
    private String tipo;      // "CLIENTE" | "FORNECEDOR"
    private Long   empresaId;
}
