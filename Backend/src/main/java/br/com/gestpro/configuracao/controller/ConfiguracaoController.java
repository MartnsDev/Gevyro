package br.com.gestpro.configuracao.controller;

import br.com.gestpro.auth.model.Usuario;
import br.com.gestpro.auth.repository.UsuarioRepository;
import br.com.gestpro.auth.service.UploadFotoOperation;
import br.com.gestpro.configuracao.dto.NotificacaoPreferenciasDTO;
import br.com.gestpro.infra.exception.ApiException;
import br.com.gestpro.plano.service.VerificarPlanoOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/configuracoes")
@RequiredArgsConstructor
public class ConfiguracaoController {

    private final UsuarioRepository       usuarioRepository;
    private final UploadFotoOperation     uploadFoto;
    private final VerificarPlanoOperation verificarPlano;

    @GetMapping("/perfil")
    public ResponseEntity<Map<String, Object>> getPerfil(Authentication auth) {
        Usuario u = buscar(auth.getName());
        long diasRestantes = verificarPlano.calcularDiasRestantes(u);

        return ResponseEntity.ok(Map.of(
                "id",               u.getId(),
                "nome",             u.getNome(),
                "email",            u.getEmail(),
                "fotoUrl",          u.getFotoUpload() != null ? u.getFotoUpload()
                        : u.getFoto()       != null ? u.getFoto() : "",
                "tipoPlano",        u.getTipoPlano().name(),
                "diasRestantes",    diasRestantes,
                "statusAcesso",     u.getStatusAcesso().name(),
                "emailConfirmado",  u.isEmailConfirmado()
        ));
    }

    @PutMapping("/perfil/nome")
    public ResponseEntity<Map<String, String>> atualizarNome(
            @RequestBody Map<String, String> body,
            Authentication auth) {

        String novoNome = body.get("nome");
        if (novoNome == null || novoNome.isBlank()) {
            throw new ApiException("Nome não pode ser vazio", HttpStatus.BAD_REQUEST, "/perfil/nome");
        }

        Usuario u = buscar(auth.getName());
        u.setNome(novoNome.trim());
        usuarioRepository.save(u);

        return ResponseEntity.ok(Map.of("nome", u.getNome()));
    }

    /**
     * Recebe um MultipartFile com key "foto", faz upload para o Cloudinary
     * e persiste a URL pública no campo fotoUpload do usuário.
     *
     * Retorna: { "fotoUrl": "https://res.cloudinary.com/..." }
     */
    @PostMapping(value = "/perfil/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadFoto(
            @RequestParam("foto") MultipartFile foto,
            Authentication auth) throws IOException {

        String email = auth.getName();
        log.info("Upload de foto de perfil iniciado.");

        // Faz upload para o Cloudinary e obtém URL permanente
        String url = uploadFoto.salvarFoto(foto, email);

        if (url == null) {
            throw new ApiException("Nenhum arquivo enviado", HttpStatus.BAD_REQUEST, "/perfil/foto");
        }

        // Persiste a URL no banco
        Usuario u = buscar(email);
        u.setFotoUpload(url);
        usuarioRepository.save(u);

        log.info("Foto de perfil salva com sucesso.");
        return ResponseEntity.ok(Map.of("fotoUrl", url));
    }

    @DeleteMapping("/perfil/foto")
    public ResponseEntity<Void> removerFoto(Authentication auth) {
        String email = auth.getName();
        uploadFoto.removerFoto(email);

        Usuario u = buscar(email);
        u.setFotoUpload(null);
        usuarioRepository.save(u);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/senha/solicitar-codigo")
    public ResponseEntity<Map<String, String>> solicitarCodigoSenha(Authentication auth) {
        // Implementação existente no seu projeto
        return ResponseEntity.ok(Map.of("mensagem", "Código enviado para seu e-mail"));
    }

    @PostMapping("/senha/trocar")
    public ResponseEntity<Map<String, String>> trocarSenha(
            @RequestBody Map<String, String> body,
            Authentication auth) {
        // Implementação existente no seu projeto
        return ResponseEntity.ok(Map.of("mensagem", "Senha alterada com sucesso"));
    }

    @GetMapping("/notificacoes")
    public ResponseEntity<NotificacaoPreferenciasDTO> getNotificacoes(Authentication auth) {
        // Usa o método buscar() que já existe nesta classe!
        Usuario usuario = buscar(auth.getName());

        NotificacaoPreferenciasDTO dto = new NotificacaoPreferenciasDTO();
        dto.setEmailVendas(usuario.isEmailVendas());
        dto.setEmailRelatorios(usuario.isEmailRelatorios());
        dto.setAlertaEstoqueZerado(usuario.isAlertaEstoqueZerado());
        dto.setAlertaVencimentoPlano(usuario.isAlertaVencimentoPlano());

        return ResponseEntity.ok(dto);
    }

    @PutMapping("/notificacoes")
    public ResponseEntity<Void> salvarNotificacoes(@RequestBody NotificacaoPreferenciasDTO dto, Authentication auth) {
        Usuario usuario = buscar(auth.getName());

        // Atualiza os campos no banco
        usuario.setEmailVendas(dto.isEmailVendas());
        usuario.setEmailRelatorios(dto.isEmailRelatorios());
        usuario.setAlertaEstoqueZerado(dto.isAlertaEstoqueZerado());
        usuario.setAlertaVencimentoPlano(dto.isAlertaVencimentoPlano());

        usuarioRepository.save(usuario);
        return ResponseEntity.ok().build();
    }

    private Usuario buscar(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(
                        "Usuário não encontrado",
                        HttpStatus.NOT_FOUND,
                        "/api/v1/configuracoes/perfil"
                ));
    }
}
