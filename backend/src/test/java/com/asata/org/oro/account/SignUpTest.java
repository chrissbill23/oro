package com.asata.org.oro.account;

import com.asata.org.oro.PostgresIntegrationTest;
import com.asata.org.oro.TestMailer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Creating an Asata account from Oro: its address verified by a link before it signs in, the same answer for an
 * address that has an account (its owner gets a notice), the links single use and for their address only.
 */
@AutoConfigureMockMvc
class SignUpTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "a long enough passphrase";

    @Autowired
    private MockMvc mvc;

    private ResultActions signUp(String email, String username, String password) throws Exception {
        return mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","username":"%s","password":"%s","givenName":"Nia","surname":"Okafor"}""".formatted(email, username, password)));
    }

    private ResultActions login(String login) throws Exception {
        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"%s\",\"password\":\"%s\"}".formatted(login, PASSWORD)));
    }

    private ResultActions verify(String token) throws Exception {
        return mvc.perform(post("/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"%s\"}".formatted(token)));
    }

    private ResultActions resend(String email) throws Exception {
        return mvc.perform(post("/auth/verify-email/resend").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"%s\"}".formatted(email)));
    }

    @Test
    void aNewAccountVerifiesItsAddressThenSignsIn() throws Exception {
        signUp("nia@example.org", "nia", PASSWORD).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(AccountService.CHECK_YOUR_EMAIL));
        assertThat(mailer.sent()).hasSize(1);
        TestMailer.Mail mail = mailer.sent().getFirst();
        assertThat(mail.to()).isEqualTo("nia@example.org");
        assertThat(mail.text()).contains("/verify-email?token=");

        // an Asata account like Umoja's: its name, its password ({bcrypt}), its address not verified yet
        Map<String, Object> row = admin.queryForMap("""
                SELECT u.email_verified, c.password_hash, p.given_name, p.surname FROM asata_user u
                JOIN asata_user_credential c ON c.user_id = u.id JOIN asata_user_personal_info p ON p.user_id = u.id
                WHERE u.username = 'nia'""");
        assertThat(row).containsEntry("email_verified", false).containsEntry("given_name", "Nia").containsEntry("surname", "Okafor");
        assertThat((String) row.get("password_hash")).startsWith("{bcrypt}");
        // the token never stored, its hash only
        assertThat(admin.queryForObject("SELECT count(*) FROM asata_user_email_token WHERE token_hash = ?", Integer.class, mail.token())).isZero();

        login("nia").andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value(AccountService.VERIFY_FIRST));
        verify(mail.token()).andExpect(status().isNoContent());
        login("nia@example.org").andExpect(status().isOk());
        // a link works once
        verify(mail.token()).andExpect(status().isBadRequest());
    }

    @Test
    void anAddressThatHasAnAccountGetsTheSameAnswerAndItsOwnerANotice() throws Exception {
        signUp("nia@example.org", "nia", PASSWORD).andExpect(status().isAccepted());
        mailer.reset();
        signUp("NIA@example.org", "someone-else", PASSWORD).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(AccountService.CHECK_YOUR_EMAIL));
        assertThat(mailer.sent()).singleElement().satisfies(notice -> {
            assertThat(notice.to()).isEqualTo("NIA@example.org");
            assertThat(notice.text()).contains("already has an Asata account").doesNotContain("token=");
        });
        assertThat(admin.queryForObject("SELECT count(*) FROM asata_user", Integer.class)).isEqualTo(1);
    }

    @Test
    void aTakenUsernameOrABadInputIsRefused() throws Exception {
        signUp("nia@example.org", "nia", PASSWORD).andExpect(status().isAccepted());
        signUp("other@example.org", "NIA", PASSWORD).andExpect(status().isConflict());
        signUp("not-an-address", "kofi", PASSWORD).andExpect(status().isBadRequest());
        signUp("kofi@example.org", "k", PASSWORD).andExpect(status().isBadRequest());
        signUp("kofi@example.org", "kofi", "short").andExpect(status().isBadRequest());
        signUp("kofi@example.org", "kofi", "kofi@example.org").andExpect(status().isBadRequest());
        // more than bcrypt reads (72 bytes): refused, never cut
        signUp("kofi@example.org", "kofi", "é".repeat(40)).andExpect(status().isBadRequest());
        assertThat(admin.queryForObject("SELECT count(*) FROM asata_user", Integer.class)).isEqualTo(1);
    }

    @Test
    void aLinkVerifiesTheAddressItWasSentToOnly() throws Exception {
        signUp("nia@example.org", "nia", PASSWORD).andExpect(status().isAccepted());
        String token = mailer.sent().getFirst().token();
        admin.update("UPDATE asata_user SET email = 'nia.new@example.org' WHERE username = 'nia'");
        verify(token).andExpect(status().isBadRequest());
        verify("not-a-token").andExpect(status().isBadRequest());
        assertThat(admin.queryForObject("SELECT email_verified FROM asata_user WHERE username = 'nia'", Boolean.class)).isFalse();
    }

    @Test
    void aNewLinkOnlyForAnAddressWaitingForIt() throws Exception {
        resend("nobody@example.org").andExpect(status().isAccepted());
        assertThat(mailer.sent()).isEmpty();

        signUp("nia@example.org", "nia", PASSWORD).andExpect(status().isAccepted());
        resend("nia@example.org").andExpect(status().isAccepted());
        assertThat(mailer.sent()).hasSize(2);
        String second = mailer.sent().get(1).token();
        assertThat(second).isNotEqualTo(mailer.sent().getFirst().token());
        verify(second).andExpect(status().isNoContent());

        mailer.reset();
        resend("nia@example.org").andExpect(status().isAccepted());
        assertThat(mailer.sent()).isEmpty();
    }
}
