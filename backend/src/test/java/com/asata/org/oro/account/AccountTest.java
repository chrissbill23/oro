package com.asata.org.oro.account;

import com.asata.org.oro.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The sign-in of Oro into Asata accounts (asata_user, shared with Umoja and Mokili): the tokens it gives work on the
 * API, the same answer for every failure, the lock after 5 wrong passwords, the rotation of the sessions and the
 * revocation of a stolen one, and the separation of the logins.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "oro.accounts.reuse-grace=0s")
class AccountTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PasswordEncoder passwordEncoder;

    /** an Asata account as Umoja's people have them: an email, a username, a password ({bcrypt}) */
    private UUID account(String username, boolean active) {
        UUID id = UUID.randomUUID();
        admin.update("INSERT INTO asata_user(id, username, email, email_verified, is_active) VALUES (?, ?, ?, true, ?)",
                id, username, username + "@example.org", active);
        admin.update("INSERT INTO asata_user_credential(user_id, password_hash) VALUES (?, ?)", id, passwordEncoder.encode(PASSWORD));
        return id;
    }

    private ResultActions login(String login, String password) throws Exception {
        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"%s\",\"password\":\"%s\",\"remember\":false}".formatted(login, password)));
    }

    private static Cookie refreshCookie(MvcResult result) {
        return result.getResponse().getCookie("oro_refresh");
    }

    private ResultActions refresh(Cookie cookie) throws Exception {
        return mvc.perform(post("/auth/refresh").cookie(cookie));
    }

    private static String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void anAsataAccountSignsInWithItsEmailOrUsernameAndUsesOro() throws Exception {
        UUID amani = account("amani", true);
        MvcResult result = login("Amani@Example.org", PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.userId").value(amani.toString()))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")).andReturn();
        login("amani", PASSWORD).andExpect(status().isOk());

        // the refresh token: an HttpOnly cookie for /auth only, never sent by another site
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("HttpOnly", "SameSite=Strict", "Path=/auth").doesNotContain("Max-Age");
        // its token: the access to Oro, as the product ORO, the Asata account as the user
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.product").value("ORO")).andExpect(jsonPath("$.userId").value(amani.toString()));

        // its session: issuer oro, hashed with the prefix: Umoja and Mokili (the SHA-256 of the token) never find it
        String refreshToken = refreshCookie(result).getValue();
        assertThat(admin.queryForObject("SELECT count(*) FROM asata_user_refresh_token WHERE token_hash = ? AND issuer = 'oro'",
                Integer.class, sha256("oro:" + refreshToken))).isEqualTo(1);
        assertThat(admin.queryForObject("SELECT count(*) FROM asata_user_refresh_token WHERE token_hash = ?",
                Integer.class, sha256(refreshToken))).isZero();
        assertThat(admin.queryForObject("SELECT last_login_at IS NOT NULL FROM asata_user WHERE id = ?", Boolean.class, amani)).isTrue();
    }

    @Test
    void everyFailureGetsTheSameAnswer() throws Exception {
        account("baraka", true);
        account("chausiku", false);
        for (ResultActions refused : new ResultActions[]{login("nobody@example.org", PASSWORD), login("baraka", "wrong password"),
                login("chausiku", PASSWORD)}) {
            refused.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(AccountService.INVALID_LOGIN));
        }
    }

    @Test
    void fiveWrongPasswordsLockTheAccountForEveryProduct() throws Exception {
        UUID baraka = account("baraka", true);
        for (int i = 0; i < 4; i++) {
            login("baraka", "wrong " + i).andExpect(status().isUnauthorized());
        }
        // counted, though refused (the transaction commits)
        assertThat(admin.queryForObject("SELECT failed_attempts FROM asata_user_credential WHERE user_id = ?", Integer.class, baraka)).isEqualTo(4);
        login("baraka", "wrong 4").andExpect(status().isUnauthorized());
        // locked in asata_user_credential, which Umoja and Mokili read too: the right password is refused meanwhile
        assertThat(admin.queryForObject("SELECT locked_until > now() FROM asata_user_credential WHERE user_id = ?", Boolean.class, baraka)).isTrue();
        login("baraka", PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void aSessionRotatesAndAStolenTokenEndsIt() throws Exception {
        account("amani", true);
        Cookie first = refreshCookie(login("amani", PASSWORD).andReturn());
        Cookie second = refreshCookie(refresh(first).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty()).andReturn());
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // the first one again: stolen (no grace in this test): the whole session ends, the second one too
        refresh(first).andExpect(status().isUnauthorized());
        refresh(second).andExpect(status().isUnauthorized());
    }

    @Test
    void signingOutClosesTheSession() throws Exception {
        account("amani", true);
        Cookie cookie = refreshCookie(login("amani", PASSWORD).andReturn());
        mvc.perform(post("/auth/logout").cookie(cookie)).andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));
        refresh(cookie).andExpect(status().isUnauthorized());
    }

    @Test
    void aDisabledAccountLosesItsSessionAtTheNextRefresh() throws Exception {
        UUID amani = account("amani", true);
        Cookie cookie = refreshCookie(login("amani", PASSWORD).andReturn());
        admin.update("UPDATE asata_user SET is_active = false WHERE id = ?", amani);
        refresh(cookie).andExpect(status().isUnauthorized());
        admin.update("UPDATE asata_user SET is_active = true WHERE id = ?", amani);
        refresh(cookie).andExpect(status().isUnauthorized());
    }

    @Test
    void aRefreshTokenOfUmojaIsNotOros() throws Exception {
        UUID amani = account("amani", true);
        // as Umoja and Mokili store theirs: the SHA-256 of the token, no issuer
        String umoja = "umoja-refresh-token-" + UUID.randomUUID();
        admin.update("""
                INSERT INTO asata_user_refresh_token(user_id, token_hash, family_id, expires_at)
                VALUES (?, ?, gen_random_uuid(), now() + interval '1 day')""", amani, sha256(umoja));
        refresh(new Cookie("oro_refresh", umoja)).andExpect(status().isUnauthorized());
    }

    @Test
    void theKeysPublishedArePublic() throws Exception {
        String jwks = mvc.perform(get("/.well-known/jwks.json")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(jwks, "$.keys[0].kty")).isEqualTo("RSA");
        assertThat(jwks).doesNotContain("\"d\"", "\"p\"", "\"q\"");
    }

    /** the logins of the messaging never read a password; the accounts' never read the messaging */
    @Test
    void theLoginsStayApart() {
        account("amani", true);
        assertThatThrownBy(() -> tx.execute(status -> {
            jdbc.execute("SET LOCAL ROLE oro_app");
            return jdbc.queryForList("SELECT password_hash FROM asata_user_credential");
        })).isInstanceOf(BadSqlGrammarException.class).rootCause().hasMessageContaining("permission denied");
        org.springframework.jdbc.core.JdbcTemplate auth = new org.springframework.jdbc.core.JdbcTemplate(
                new org.springframework.jdbc.datasource.SingleConnectionDataSource(POSTGRES.getJdbcUrl(), "oro_auth", "oro_auth_test", true));
        auth.execute("SET ROLE oro_account");
        assertThatThrownBy(() -> auth.queryForList("SELECT body FROM oro_message")).rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> auth.queryForList("SELECT * FROM oro_messaging_identity")).rootCause().hasMessageContaining("permission denied");
        // nor the columns of an account it does not need
        assertThatThrownBy(() -> auth.update("UPDATE asata_user SET email = 'x@example.org'")).rootCause().hasMessageContaining("permission denied");
    }
}
