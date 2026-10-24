package com.asata.org.oro.account;

import com.asata.org.oro.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Choosing a new password by a link sent by email, and the limits of the public routes of the accounts: per client
 * address, and per recipient of the emails.
 */
@AutoConfigureMockMvc
class PasswordResetTest extends PostgresIntegrationTest {

    private static final String OLD = "the old passphrase";
    private static final String NEW = "a brand new passphrase";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private UUID account(String username) {
        UUID id = UUID.randomUUID();
        admin.update("INSERT INTO asata_user(id, username, email, email_verified) VALUES (?, ?, ?, false)",
                id, username, username + "@example.org");
        admin.update("INSERT INTO asata_user_credential(user_id, password_hash, failed_attempts, locked_until) VALUES (?, ?, 0, now() + interval '10 minutes')",
                id, passwordEncoder.encode(OLD));
        return id;
    }

    private ResultActions post(String path, String json) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions login(String password) throws Exception {
        return post("/auth/login", "{\"login\":\"amani\",\"password\":\"%s\"}".formatted(password));
    }

    @Test
    void aLinkResetsThePasswordAndEndsEverySession() throws Exception {
        UUID amani = account("amani");
        // a session of Umoja, and one of Oro (the account is locked and unverified: given by hand)
        admin.update("INSERT INTO asata_user_refresh_token(user_id, token_hash, family_id, expires_at) VALUES (?, ?, gen_random_uuid(), now() + interval '1 day')",
                amani, "a".repeat(64));
        admin.update("INSERT INTO asata_user_refresh_token(user_id, token_hash, family_id, expires_at, issuer) VALUES (?, ?, gen_random_uuid(), now() + interval '1 day', 'oro')",
                amani, "b".repeat(64));

        post("/auth/password/forgot", "{\"email\":\"Amani@Example.org\"}").andExpect(status().isAccepted());
        assertThat(mailer.sent()).singleElement().satisfies(mail -> assertThat(mail.text()).contains("/reset-password?token="));
        String token = mailer.sent().getFirst().token();

        // a weak password: refused, and the link still works
        post("/auth/password/reset", "{\"token\":\"%s\",\"password\":\"short\"}".formatted(token)).andExpect(status().isBadRequest());
        post("/auth/password/reset", "{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, NEW)).andExpect(status().isNoContent());
        post("/auth/password/reset", "{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, NEW)).andExpect(status().isBadRequest());

        // every session closed, in every product; unlocked; the address verified (the link reached it); a notice
        assertThat(admin.queryForObject("SELECT count(*) FROM asata_user_refresh_token WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class, amani)).isZero();
        assertThat(mailer.sent()).hasSize(2);
        assertThat(mailer.sent().get(1).text()).contains("was just changed");
        login(OLD).andExpect(status().isUnauthorized());
        login(NEW).andExpect(status().isOk());
    }

    @Test
    void anUnknownAddressGetsTheSameAnswerAndNoEmail() throws Exception {
        post("/auth/password/forgot", "{\"email\":\"nobody@example.org\"}").andExpect(status().isAccepted());
        assertThat(mailer.sent()).isEmpty();
        post("/auth/password/reset", "{\"token\":\"made-up\",\"password\":\"%s\"}".formatted(NEW)).andExpect(status().isBadRequest());
    }

    @Test
    void aVerificationLinkDoesNotResetAPassword() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nia@example.org\",\"username\":\"nia\",\"password\":\"%s\"}".formatted(OLD))).andExpect(status().isAccepted());
        String verification = mailer.sent().getFirst().token();
        post("/auth/password/reset", "{\"token\":\"%s\",\"password\":\"%s\"}".formatted(verification, NEW)).andExpect(status().isBadRequest());
    }

    @Test
    void theSignInIsLimitedPerClientAddress() throws Exception {
        account("amani");
        int refused = 0;
        for (int i = 0; i < 40; i++) {
            int code = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/auth/login")
                    .with(request -> {
                        request.setRemoteAddr("203.0.113.7");
                        return request;
                    }).contentType(MediaType.APPLICATION_JSON).content("{\"login\":\"x%d\",\"password\":\"wrong\"}".formatted(i)))
                    .andReturn().getResponse().getStatus();
            refused += code == 429 ? 1 : 0;
        }
        // 30 at once, then 30 a minute: the next ones wait
        assertThat(refused).isBetween(9, 10);
        // another address is not concerned
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/auth/login")
                .with(request -> {
                    request.setRemoteAddr("198.51.100.9");
                    return request;
                }).contentType(MediaType.APPLICATION_JSON).content("{\"login\":\"x\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oneRecipientGetsAFewEmailsWhoeverAsks() throws Exception {
        account("amani");
        for (int i = 0; i < 6; i++) {
            final int n = i;
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/auth/password/forgot")
                    .with(request -> {
                        request.setRemoteAddr("192.0.2." + n);
                        return request;
                    }).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"amani@example.org\"}"))
                    .andExpect(status().isAccepted());
        }
        assertThat(mailer.sent()).hasSize(3);
    }
}
