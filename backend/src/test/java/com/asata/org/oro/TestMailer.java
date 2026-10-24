package com.asata.org.oro;

import com.asata.org.oro.account.Mailer;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** the emails of the tests: recorded, never sent */
@Primary
@TestComponent
public class TestMailer implements Mailer {

    public record Mail(String to, String subject, String text) {
        /** the token of the link of the email */
        public String token() {
            Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(text);
            if (!m.find()) {
                throw new AssertionError("no link in: " + text);
            }
            return m.group(1);
        }
    }

    private final List<Mail> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(String to, String subject, String text) {
        sent.add(new Mail(to, subject, text));
    }

    public List<Mail> sent() {
        return sent;
    }

    public void reset() {
        sent.clear();
    }
}
