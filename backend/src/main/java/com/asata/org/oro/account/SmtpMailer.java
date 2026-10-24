package com.asata.org.oro.account;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** by SMTP (spring.mail.*): any provider, or Mailpit in development */
@Component
@RequiredArgsConstructor
public class SmtpMailer implements Mailer {

    private final JavaMailSender sender;
    private final AccountProperties properties;

    @Override
    public void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMailFrom());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        sender.send(message);
    }
}
