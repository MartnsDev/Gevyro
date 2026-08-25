package br.com.gestpro.configuracao.service;

import br.com.gestpro.auth.EmailService;
import br.com.gestpro.auth.model.Usuario;
import br.com.gestpro.auth.repository.UsuarioRepository;
import br.com.gestpro.configuracao.dto.NotificacoesDTO;
import br.com.gestpro.configuracao.dto.PerfilDTO;
import br.com.gestpro.configuracao.dto.TrocarSenhaDTO;
import br.com.gestpro.infra.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

@Service
@RequiredArgsConstructor
public class ConfiguracaoServiceImpl implements ConfiguracaoServiceInterface {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder   passwordEncoder;
    private final EmailService      emailService;
    private final StringRedisTemplate redis;

    // Códigos temporários em memória: email → {codigo, expiracao}
    private final Map<String, CodigoTemp> codigos = new ConcurrentHashMap<>();

    private record CodigoTemp(String codigo, LocalDateTime expiracao) {}

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Perfil
    @Override
    public PerfilDTO getPerfil(String email) {
        Usuario u = buscar(email);

        // Dias restantes:
        // - Planos pagos: dataAssinaturaPlus + duração do plano
        // - EXPERIMENTAL: dataPrimeiroLogin + 7 dias
        int diasRestantes = 0;
        if (u.getTipoPlano() != null) {
            LocalDateTime referencia = u.getDataAssinaturaPlus() != null
                    ? u.getDataAssinaturaPlus()
                    : u.getDataPrimeiroLogin();

            if (referencia != null) {
                int duracaoDias = u.getTipoPlano().getDuracaoDiasPadrao();
                LocalDate fim = referencia.toLocalDate().plusDays(duracaoDias);
                diasRestantes = (int) Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), fim));
            }
        }

        // fotoUrl: prioriza upload, depois Google
        String fotoUrl = u.getFotoUpload() != null ? u.getFotoUpload() : u.getFoto();

        // data de assinatura formatada para exibição
        String dataAssinatura = null;
        if (u.getDataAssinaturaPlus() != null) {
            dataAssinatura = u.getDataAssinaturaPlus().format(FMT);
        } else if (u.getDataPrimeiroLogin() != null) {
            dataAssinatura = u.getDataPrimeiroLogin().format(FMT);
        }

        return PerfilDTO.builder()
                .id(u.getId())
                .nome(u.getNome())
                .email(u.getEmail())
                .fotoUrl(fotoUrl)
                .tipoPlano(u.getTipoPlano() != null ? u.getTipoPlano().name() : "EXPERIMENTAL")
                .diasRestantes(diasRestantes)
                .statusAcesso(u.getStatusAcesso() != null ? u.getStatusAcesso().name() : "ATIVO")
                .dataAssinatura(dataAssinatura)
                .emailConfirmado(u.isEmailConfirmado())
                .build();
    }

    @Override
    public void atualizarNome(String email, String novoNome) {
        if (novoNome == null || novoNome.isBlank())
            throw new ApiException("Nome inválido.", HttpStatus.BAD_REQUEST, "/configuracoes/perfil/nome");
        Usuario u = buscar(email);
        u.setNome(novoNome.trim());
        usuarioRepository.save(u);
    }

    @Override
    public String uploadFoto(String email, MultipartFile foto) {
        if (foto == null || foto.isEmpty())
            throw new ApiException("Arquivo vazio.", HttpStatus.BAD_REQUEST, "/configuracoes/perfil/foto");

        if (foto.getSize() > 5L * 1024 * 1024)
            throw new ApiException("A foto deve ter no máximo 5 MB.", HttpStatus.BAD_REQUEST, "/configuracoes/perfil/foto");

        String contentType = foto.getContentType();
        String formato = switch (contentType == null ? "" : contentType.toLowerCase()) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            default -> throw new ApiException("Envie uma imagem JPEG ou PNG.",
                    HttpStatus.BAD_REQUEST, "/configuracoes/perfil/foto");
        };
        String nomeArquivo = UUID.randomUUID() + "." + formato;

        Path destino = Paths.get("uploads/fotos/" + nomeArquivo);
        try {
            BufferedImage imagem = ImageIO.read(foto.getInputStream());
            if (imagem == null || (long) imagem.getWidth() * imagem.getHeight() > 25_000_000L)
                throw new ApiException("Imagem inválida ou com dimensões excessivas.",
                        HttpStatus.BAD_REQUEST, "/configuracoes/perfil/foto");
            Files.createDirectories(destino.getParent());
            if (!ImageIO.write(imagem, formato, destino.toFile()))
                throw new IOException("Formato de imagem não suportado");
        } catch (IOException e) {
            throw new ApiException("Erro ao salvar foto.", HttpStatus.INTERNAL_SERVER_ERROR, "/configuracoes/perfil/foto");
        }


        String url = "/uploads/fotos/" + nomeArquivo;
        Usuario u = buscar(email);
        u.setFotoUpload(url);
        usuarioRepository.save(u);
        return url;
    }

    @Override
    public void solicitarCodigoTrocaSenha(String email) {
        Usuario u = buscar(email);

        // Usuários Google sem senha local não podem trocar senha por aqui
        if (u.isLoginGoogle() && u.getSenha() == null)
            throw new ApiException(
                    "Sua conta é vinculada ao Google. Troque a senha diretamente pelo Google.",
                    HttpStatus.BAD_REQUEST, "/configuracoes/senha");

        Boolean permitido = redis.opsForValue().setIfAbsent("config:senha:envio:" + email,
                "1", Duration.ofSeconds(60));
        if (!Boolean.TRUE.equals(permitido))
            throw new ApiException("Aguarde antes de solicitar outro código.",
                    HttpStatus.TOO_MANY_REQUESTS, "/configuracoes/senha");
        String codigo = emailService.gerarCodigo();
        codigos.put(email, new CodigoTemp(passwordEncoder.encode(codigo), LocalDateTime.now().plusMinutes(10)));
        redis.delete("config:senha:tentativas:" + email);
        emailService.enviarCodigoConfirmacao(email, u.getNome(), codigo);
    }

    @Override
    public void trocarSenha(String email, TrocarSenhaDTO dto) {
        CodigoTemp ct = codigos.get(email);

        Long tentativas = redis.opsForValue().increment("config:senha:tentativas:" + email);
        if (tentativas != null && tentativas == 1)
            redis.expire("config:senha:tentativas:" + email, Duration.ofMinutes(10));
        if (tentativas != null && tentativas > 5) {
            codigos.remove(email);
            throw new ApiException("Código inválido ou não solicitado.",
                    HttpStatus.BAD_REQUEST, "/configuracoes/senha/trocar");
        }
        if (ct == null || !passwordEncoder.matches(dto.getCodigo(), ct.codigo()))
            throw new ApiException("Código inválido ou não solicitado.",
                    HttpStatus.BAD_REQUEST, "/configuracoes/senha/trocar");

        if (LocalDateTime.now().isAfter(ct.expiracao())) {
            codigos.remove(email);
            throw new ApiException("Código expirado. Solicite um novo.",
                    HttpStatus.BAD_REQUEST, "/configuracoes/senha/trocar");
        }

        if (!dto.getNovaSenha().equals(dto.getConfirmarSenha()))
            throw new ApiException("As senhas não coincidem.",
                    HttpStatus.BAD_REQUEST, "/configuracoes/senha/trocar");

        if (dto.getNovaSenha().length() < 6)
            throw new ApiException("Senha deve ter ao menos 6 caracteres.",
                    HttpStatus.BAD_REQUEST, "/configuracoes/senha/trocar");

        Usuario u = buscar(email);
        u.setSenha(passwordEncoder.encode(dto.getNovaSenha()));
        usuarioRepository.save(u);
        codigos.remove(email);
        redis.delete("config:senha:tentativas:" + email);
        redis.delete("config:senha:envio:" + email);
    }

    @Override
    public NotificacoesDTO getNotificacoes(String email) {
        buscar(email); // valida existência
        NotificacoesDTO dto = new NotificacoesDTO();
        dto.setEmailVendas(true);
        dto.setEmailRelatorios(false);
        dto.setAlertaEstoqueZerado(true);
        dto.setAlertaVencimentoPlano(true);
        return dto;
    }

    @Override
    public void atualizarNotificacoes(String email, NotificacoesDTO dto) {
        buscar(email); // valida existência — expanda para persistir se necessário
    }

    private Usuario buscar(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("Usuário não encontrado.",
                        HttpStatus.NOT_FOUND, "/configuracoes"));
    }
}
