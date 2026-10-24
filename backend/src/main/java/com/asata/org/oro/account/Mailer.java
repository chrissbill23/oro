package com.asata.org.oro.account;

/** sends an email of the accounts (a verification, a reset): plain text, one recipient */
public interface Mailer {

    void send(String to, String subject, String text);
}
