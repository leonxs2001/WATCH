package de.thb.kritis_elfe.mail;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

@Service("emailService")
@AllArgsConstructor
public class EmailService implements EmailSender{

    private final static Logger LOGGER = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender javaMailSender;

    private final Environment env;

    @Override
    @Async
    public void send(String to, String email) {

        try{
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");

            helper.setText(email, true);
            helper.setTo(to);
            helper.setSubject("Änderung auf KRITIS-ELFe");
            helper.setFrom(env.getProperty("spring.mail.username"));
            javaMailSender.send(mimeMessage);
        }catch (MessagingException e){
            LOGGER.error("Fehler beim Senden der Nachricht", e);
            throw new IllegalStateException("Failed to send mail");
        }
    }

    public void sendMailFromTemplate(String template, Context context, String mailAddress){
        new Thread(() -> {
            ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
            templateResolver.setSuffix(".html");
            templateResolver.setTemplateMode(TemplateMode.HTML);

            TemplateEngine templateEngine = new TemplateEngine();
            templateEngine.setTemplateResolver(templateResolver);

            String emailText = templateEngine.process("templates/" + template, context);
            send(mailAddress, emailText);
        }).start();

    }
}

