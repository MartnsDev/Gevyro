package br.com.gestpro.auth.service;

import br.com.gestpro.infra.exception.ApiException;
import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class UploadFotoOperation {

    private final Cloudinary cloudinary;

    public String salvarFoto(MultipartFile foto, String emailUsuario) throws IOException {
        if (foto == null || foto.isEmpty()) {
            return null;
        }

        // Valida tipo MIME
        String contentType = foto.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ApiException(
                    "Arquivo deve ser uma imagem (JPG, PNG ou WebP)",
                    HttpStatus.BAD_REQUEST,
                    "/api/v1/configuracoes/perfil/foto"
            );
        }

        // Valida tamanho — máx 5MB
        if (foto.getSize() > 5L * 1024 * 1024) {
            throw new ApiException(
                    "A imagem deve ter no máximo 5MB",
                    HttpStatus.BAD_REQUEST,
                    "/api/v1/configuracoes/perfil/foto"
            );
        }

        // Public ID único por usuário
        String publicId = "gestpro/fotos/usuario_"
                + emailUsuario.replace("@", "_at_").replace(".", "_");

        log.info("Enviando foto de perfil para o armazenamento.");

        Map params = ObjectUtils.asMap(
                "public_id",      publicId,
                "overwrite",      true,
                "invalidate",     true,
                "transformation", new Transformation()
                        .width(200)
                        .height(200)
                        .crop("fill")
                        .gravity("face") // Foca no rosto se detectado
                        .quality("auto")
                        .fetchFormat("auto")
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> resultado = cloudinary.uploader().upload(foto.getBytes(), params);

        String url = (String) resultado.get("secure_url");
        log.info("Upload da foto de perfil concluído.");
        return url;
    }

    public void removerFoto(String emailUsuario) {
        String publicId = "gestpro/fotos/usuario_"
                + emailUsuario.replace("@", "_at_").replace(".", "_");
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("invalidate", true));
            log.info("Foto de perfil removida do armazenamento.");
        } catch (Exception e) {
            log.warn("Não foi possível remover a foto de perfil do armazenamento.");
        }
    }
}
