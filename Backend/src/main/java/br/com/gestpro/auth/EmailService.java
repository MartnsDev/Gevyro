package br.com.gestpro.auth;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class EmailService {

    @Value("${resend.api.key}")
    private String resendApiKey;

    @Value("${RESEND_FROM_EMAIL:Gevyro <suporte@gevyro.com.br>}")
    private String resendFromEmail;

    private static final String EMAIL_SUPORTE = "suporte@gevyro.com.br";

    private String templateBase(String preheader, String corpoHtml) {
        return """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
              <meta charset="UTF-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
              <title>Gevyro</title>
              <!--[if mso]><noscript><xml><o:OfficeDocumentSettings>
              <o:PixelsPerInch>96</o:PixelsPerInch></o:OfficeDocumentSettings>
              </xml></noscript><![endif]-->
            </head>
            <body style="margin:0;padding:0;background-color:#0f0f13;font-family:'Segoe UI',Helvetica,Arial,sans-serif;">
              <!-- preheader oculto -->
              <span style="display:none;max-height:0;overflow:hidden;">%s</span>

              <!-- wrapper -->
              <table width="100%%" cellpadding="0" cellspacing="0" border="0"
                     style="background-color:#0f0f13;padding:40px 16px;">
                <tr>
                  <td align="center">
                    <table width="100%%" cellpadding="0" cellspacing="0" border="0"
                           style="max-width:580px;">

                      <!-- ── LOGO ── -->
                      <tr>
                        <td align="center" style="padding-bottom:28px;">
                          <table cellpadding="0" cellspacing="0" border="0">
                            <tr>
                              <td style="background:linear-gradient(135deg,#6c63ff,#3b82f6);
                                         border-radius:14px;padding:10px 22px;">
                                <span style="font-size:22px;font-weight:800;color:#ffffff;
                                             letter-spacing:1.5px;font-family:'Segoe UI',sans-serif;">
                                  GEVYRO
                                </span>
                                <span style="display:block;margin-top:4px;font-size:10px;
                                             font-weight:600;color:#d1fae5;letter-spacing:0.8px;">
                                  Gestão em evolução.
                                </span>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>

                      <!-- ── CARD PRINCIPAL ── -->
                      <tr>
                        <td style="background:#1a1a24;border-radius:20px;
                                   border:1px solid #2a2a3a;
                                   box-shadow:0 24px 60px rgba(0,0,0,0.5);
                                   overflow:hidden;">
                          %s
                        </td>
                      </tr>

                      <!-- ── RODAPÉ ── -->
                      <tr>
                        <td align="center" style="padding-top:28px;">
                          <p style="margin:0 0 6px;font-size:12px;color:#4a4a6a;">
                            Precisa de ajuda?
                            <a href="mailto:%s"
                               style="color:#6c63ff;text-decoration:none;">%s</a>
                          </p>
                          <p style="margin:0;font-size:11px;color:#333350;">
                            © 2026 Gevyro · Este e-mail é automático, não responda diretamente.
                          </p>
                        </td>
                      </tr>

                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
        """.formatted(preheader, corpoHtml, EMAIL_SUPORTE, EMAIL_SUPORTE);
    }

    //  FAIXA HERO COLORIDA — topo do card com ícone e título
    private String hero(String gradiente, String emoji, String titulo, String subtitulo) {
        return """
            <div style="background:%s;padding:40px 32px 32px;text-align:center;">
              <div style="font-size:48px;line-height:1;margin-bottom:14px;">%s</div>
              <h1 style="margin:0 0 8px;font-size:26px;font-weight:800;color:#ffffff;
                         letter-spacing:-0.5px;">%s</h1>
              <p style="margin:0;font-size:14px;color:rgba(255,255,255,0.75);">%s</p>
            </div>
        """.formatted(gradiente, emoji, titulo, subtitulo);
    }

    //  EMAIL GENÉRICO
    public void enviarEmail(String to, String nomeUsuario, String subject, String body) {
        String nome = (nomeUsuario != null && !nomeUsuario.isBlank()) ? nomeUsuario : "usuário";

        String corpo = hero(
                "linear-gradient(135deg,#6c63ff 0%%,#3b82f6 100%%)",
                "📩", "Você tem uma mensagem", "Gevyro · Comunicado"
        ) + """
            <div style="padding:32px;color:#c8c8e0;">
              <p style="margin:0 0 16px;font-size:16px;color:#e0e0f0;">
                Olá, <strong style="color:#ffffff;">%s</strong>!
              </p>
              <p style="margin:0 0 24px;font-size:15px;line-height:1.7;color:#a0a0c0;">%s</p>
              <div style="border-top:1px solid #2a2a3a;padding-top:20px;
                          font-size:13px;color:#555570;">
                Dúvidas? Fale com
                <a href="mailto:%s"
                   style="color:#6c63ff;text-decoration:none;">%s</a>
              </div>
            </div>
        """.formatted(nome, body, EMAIL_SUPORTE, EMAIL_SUPORTE);

        enviarHtml(to, subject, templateBase(body.substring(0, Math.min(80, body.length())), corpo));
    }

    //  CONFIRMAÇÃO DE CONTA
    public void enviarConfirmacao(String emailDestino, String linkConfirmacao) {
        String corpo = hero(
                "linear-gradient(135deg,#10b981 0%%,#059669 100%%)",
                "🚀", "Bem-vindo ao Gevyro!", "Você está a um clique de começar"
        ) + """
            <div style="padding:36px 32px;">
              <p style="margin:0 0 12px;font-size:16px;color:#e0e0f0;line-height:1.6;">
                Sua conta foi criada com sucesso.
                Confirme seu e-mail para ativar todos os recursos da plataforma.
              </p>

              <!-- botão CTA -->
              <table cellpadding="0" cellspacing="0" border="0"
                     style="margin:32px auto;">
                <tr>
                  <td align="center"
                      style="background:linear-gradient(135deg,#10b981,#059669);
                             border-radius:12px;">
                    <a href="%s"
                       style="display:inline-block;padding:16px 40px;
                              font-size:16px;font-weight:700;
                              color:#ffffff;text-decoration:none;
                              letter-spacing:0.5px;">
                      ✔&nbsp;&nbsp;Confirmar minha conta
                    </a>
                  </td>
                </tr>
              </table>

              <!-- link fallback -->
              <div style="background:#111120;border-radius:10px;
                          padding:16px 20px;margin-top:8px;">
                <p style="margin:0 0 6px;font-size:12px;color:#555570;
                           text-transform:uppercase;letter-spacing:0.8px;">
                  Ou copie o link abaixo:
                </p>
                <p style="margin:0;font-size:12px;color:#6c63ff;
                           word-break:break-all;line-height:1.5;">%s</p>
              </div>

              <p style="margin:24px 0 0;font-size:12px;color:#555570;line-height:1.6;">
                ⏱ Este link expira em <strong style="color:#a0a0c0;">24 horas</strong>.
                Se você não criou essa conta, ignore este e-mail.
              </p>
            </div>
        """.formatted(linkConfirmacao, linkConfirmacao);

        enviarHtml(emailDestino, "Confirme seu e-mail · Gevyro",
                templateBase("Ative sua conta Gevyro — clique para confirmar seu e-mail.", corpo));
    }

    //  CÓDIGO DE VERIFICAÇÃO — exibição grande + botão copiar via JS
    public void enviarCodigoConfirmacao(String emailDestino, String nomeUsuario, String codigo) {
        String nome = (nomeUsuario != null && !nomeUsuario.isBlank()) ? nomeUsuario : "usuário";

        // Separa os dígitos em spans individuais para espaçamento elegante
        StringBuilder digitos = new StringBuilder();
        for (char c : codigo.toCharArray()) {
            digitos.append("""
                <span style="display:inline-block;width:52px;height:64px;line-height:64px;
                             text-align:center;font-size:36px;font-weight:800;color:#ffffff;
                             background:#23233a;border-radius:12px;
                             border:1.5px solid #3a3a5a;margin:0 4px;
                             font-family:'Courier New',monospace;letter-spacing:0;">
                  %s
                </span>
            """.formatted(c));
        }

        String corpo = hero(
                "linear-gradient(135deg,#7c3aed 0%%,#4f46e5 100%%)",
                "🔐", "Código de Verificação", "Use este código para confirmar sua ação"
        ) + """
            <div style="padding:36px 32px;">
              <p style="margin:0 0 8px;font-size:16px;color:#e0e0f0;">
                Olá, <strong style="color:#ffffff;">%s</strong>!
              </p>
              <p style="margin:0 0 28px;font-size:14px;color:#7070a0;line-height:1.6;">
                Seu código de verificação Gevyro está abaixo.
                Digite-o na tela para continuar.
              </p>

              <!-- bloco do código -->
              <div style="background:#111120;border-radius:16px;
                          padding:28px 20px;text-align:center;
                          border:1px solid #2a2a3a;">
                <div style="margin-bottom:20px;">
                  %s
                </div>

                <!-- botão copiar (funciona em clientes que renderizam JS — fallback visual nos demais) -->
                <div id="copy-wrap" style="margin-top:4px;">
                  <button onclick="
                    navigator.clipboard.writeText('%s').then(function(){
                      var btn = document.getElementById('copy-btn');
                      btn.textContent = '✓  Copiado!';
                      btn.style.background = 'linear-gradient(135deg,#10b981,#059669)';
                      setTimeout(function(){ btn.textContent = '⧉  Copiar código'; btn.style.background = 'linear-gradient(135deg,#6c63ff,#4f46e5)'; }, 2500);
                    })
                  "
                    id="copy-btn"
                    style="cursor:pointer;border:none;outline:none;
                           background:linear-gradient(135deg,#6c63ff,#4f46e5);
                           color:#ffffff;font-size:14px;font-weight:700;
                           padding:12px 32px;border-radius:10px;
                           letter-spacing:0.5px;transition:all .3s;">
                    ⧉&nbsp;&nbsp;Copiar código
                  </button>
                </div>
              </div>

              <!-- avisos -->
              <div style="margin-top:24px;display:flex;flex-direction:column;gap:8px;">
                <div style="background:#1e1a2e;border-left:3px solid #f59e0b;
                             border-radius:0 8px 8px 0;padding:12px 16px;">
                  <p style="margin:0;font-size:13px;color:#f59e0b;font-weight:600;">
                    ⏱&nbsp; Expira em <strong>10 minutos</strong>
                  </p>
                </div>
                <div style="background:#1a1e1e;border-left:3px solid #ef4444;
                             border-radius:0 8px 8px 0;padding:12px 16px;">
                  <p style="margin:0;font-size:13px;color:#ef4444;font-weight:600;">
                    🔒&nbsp; Nunca compartilhe este código com ninguém
                  </p>
                </div>
              </div>

              <p style="margin:24px 0 0;font-size:12px;color:#555570;line-height:1.6;">
                Não solicitou este código?
                <a href="mailto:%s"
                   style="color:#6c63ff;text-decoration:none;">
                  Entre em contato imediatamente.
                </a>
              </p>
            </div>
        """.formatted(nome, digitos.toString(), codigo, EMAIL_SUPORTE);

        enviarHtml(emailDestino, "Seu código de verificação · Gevyro",
                templateBase("Seu código Gevyro: " + codigo + " — expira em 10 minutos.", corpo));
    }

    //  ENVIO CENTRAL via Resend API
    private void enviarHtml(String to, String subject, String html) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            throw new RuntimeException("RESEND_API_KEY não configurada no ambiente");
        }
        try {
            if (resendFromEmail == null || resendFromEmail.isBlank()) {
                throw new RuntimeException("RESEND_FROM_EMAIL não configurada no ambiente");
            }

            String json = new Gson().toJson(Map.of(
                    "from", resendFromEmail.trim(),
                    "to", List.of(to),
                    "subject", subject,
                    "html", html
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + resendApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                log.info("E-mail aceito pela Resend: status={}", response.statusCode());
            } else {
                log.error("Resend recusou o envio: status={}", response.statusCode());
                throw new RuntimeException("Resend recusou o envio com status " + response.statusCode());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Falha ao enviar e-mail: " + e.getMessage(), e);
        }
    }

    //  GERADOR DE CÓDIGO 6 DÍGITOS
    public String gerarCodigo() {
        int codigo = 100000 + (int) (Math.random() * 900000);
        return String.valueOf(codigo);
    }
}
