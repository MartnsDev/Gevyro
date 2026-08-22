package br.com.gestpro.auth;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class MailTrapEmail {

    private final JavaMailSender mailSender;

    public MailTrapEmail(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void enviarConfirmacao(String emailDestino, String linkConfirmacao) {

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("Gevyro <suporte@gevyro.com.br>");
            helper.setTo(emailDestino);
            helper.setSubject("Confirme seu e-mail - Gevyro");

            String html = """
                <div style="font-family: Arial, sans-serif; background-color:#f4f6f8; padding:20px;">
                    <div style="max-width:600px; margin:auto; background:white; border-radius:10px; padding:30px; box-shadow:0 2px 10px rgba(0,0,0,0.1);">
                        
                        <h2 style="color:#333;">🚀 Bem-vindo ao Gevyro!</h2>
                        
                        <p style="color:#555; font-size:16px;">
                            Obrigado por se cadastrar. Para ativar sua conta, clique no botão abaixo:
                        </p>

                        <div style="text-align:center; margin:30px 0;">
                            <a href="%s" style="
                                background-color:#4CAF50;
                                color:white;
                                padding:15px 25px;
                                text-decoration:none;
                                border-radius:5px;
                                font-size:16px;
                                font-weight:bold;
                                display:inline-block;
                            ">
                                ✔ Confirmar Conta
                            </a>
                        </div>

                        <p style="color:#777; font-size:14px;">
                            Ou copie e cole este link no navegador:
                        </p>

                        <p style="word-break: break-all; color:#555; font-size:13px;">
                            %s
                        </p>

                        <hr style="margin:30px 0; border:none; border-top:1px solid #eee;">

                        <p style="color:#999; font-size:12px;">
                            Se você não criou uma conta, pode ignorar este e-mail com segurança.
                        </p>

                        <p style="color:#999; font-size:12px;">
                            © 2026 Gevyro
                        </p>
                    </div>
                </div>
            """.formatted(linkConfirmacao, linkConfirmacao);

            helper.setText(html, true);

            mailSender.send(message);

        } catch (Exception e) {
            throw new RuntimeException("Erro ao enviar email: " + e.getMessage());
        }
    }
}
