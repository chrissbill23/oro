package com.asata.org.oro.api;

import com.asata.org.oro.PostgresIntegrationTest;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The HTTP API through its whole chain: tokens really signed by each product (HS256, their own secret and issuer),
 * the integration keys, the organization header, the JSON of the responses and errors.
 */
@AutoConfigureMockMvc
class ApiTest extends PostgresIntegrationTest {

    private static final String ORO_SECRET = "test-oro-jwt-secret-for-the-tests-only-0123456789";
    private static final String UMOJA_SECRET = "test-umoja-jwt-secret-for-the-tests-only-0123456789";
    private static final String ORO_KEY = "test-oro-integration-key-for-the-tests-0123456789";
    private static final String UMOJA_KEY = "test-umoja-integration-key-for-the-tests-0123456789";

    @Autowired
    private MockMvc mvc;

    private static String token(String issuer, String secret, String subject, Instant expiresAt) {
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(subject)
                .issuedAt(expiresAt.minus(15, ChronoUnit.MINUTES)).expiresAt(expiresAt).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private static String oro(String userId) {
        return token("oro", ORO_SECRET, userId, Instant.now().plus(10, ChronoUnit.MINUTES));
    }

    private static String umoja(String userId) {
        return token("umoja", UMOJA_SECRET, userId, Instant.now().plus(10, ChronoUnit.MINUTES));
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token, String organizationId, String json)
            throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (organizationId != null) {
            request.header("x-organization-id", organizationId);
        }
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(request);
    }

    private ResultActions integration(MockHttpServletRequestBuilder request, String key) throws Exception {
        return mvc.perform(key == null ? request : request.header("x-integration-key", key));
    }

    private String idOf(ResultActions result) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void onlyTrustedTokensGetIn() throws Exception {
        call(get("/ping"), null, null, null).andExpect(status().isOk()).andExpect(jsonPath("$.serverStatus").value("UP"));
        call(get("/conversations"), null, null, null).andExpect(status().isUnauthorized())
                .andExpect(header().exists("x-request-id")).andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        // an Oro issuer signed with the Umoja secret, an unknown issuer, an expired token, garbage
        call(get("/conversations"), token("oro", UMOJA_SECRET, "oro-amani", Instant.now().plusSeconds(600)), null, null)
                .andExpect(status().isUnauthorized());
        call(get("/conversations"), token("mokili", ORO_SECRET, "oro-amani", Instant.now().plusSeconds(600)), null, null)
                .andExpect(status().isUnauthorized());
        call(get("/conversations"), token("oro", ORO_SECRET, "oro-amani", Instant.now().minusSeconds(600)), null, null)
                .andExpect(status().isUnauthorized());
        call(get("/conversations"), "not-a-token", null, null).andExpect(status().isUnauthorized());
        assertNoIdentity();
        call(get("/doc/api"), null, null, null).andExpect(status().isOk());
    }

    private void assertNoIdentity() {
        org.assertj.core.api.Assertions.assertThat(count("SELECT count(*) FROM oro_messaging_identity")).isZero();
    }

    @Test
    void anOroConversationFromEndToEnd() throws Exception {
        String amani = oro("oro-amani");
        String baraka = oro("oro-baraka");
        call(get("/me"), amani, null, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.product").value("ORO")).andExpect(jsonPath("$.userId").value("oro-amani"))
                .andExpect(jsonPath("$.organizationId").value(nullValue()));
        // Baraka never came: unknown to Oro
        call(post("/conversations/direct"), amani, null, "{\"userId\":\"oro-baraka\"}").andExpect(status().isNotFound());
        call(get("/me"), baraka, null, null).andExpect(status().isOk());

        String direct = idOf(call(post("/conversations/direct"), amani, null, "{\"userId\":\"oro-baraka\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("DIRECT"))
                .andExpect(jsonPath("$.members[*].userId", containsInAnyOrder("oro-amani", "oro-baraka"))));
        call(post("/conversations/direct"), baraka, null, "{\"userId\":\"oro-amani\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(direct));

        String send = "{\"clientMessageId\":\"client-0001\",\"type\":\"TEXT\",\"body\":\"Hello Baraka\",\"metadata\":{\"mood\":\"happy\"}}";
        String messageId = idOf(call(post("/conversations/" + direct + "/messages"), amani, null, send)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.seq").value(1))
                .andExpect(jsonPath("$.senderUserId").value("oro-amani")).andExpect(jsonPath("$.metadata.mood").value("happy")));
        call(post("/conversations/" + direct + "/messages"), amani, null, send)
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(messageId));

        call(get("/conversations"), baraka, null, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(direct)).andExpect(jsonPath("$[0].unreadCount").value(1));
        call(get("/conversations/" + direct + "/messages"), baraka, null, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(1))).andExpect(jsonPath("$.messages[0].body").value("Hello Baraka"))
                .andExpect(jsonPath("$.nextBeforeSeq").value(nullValue()));
        call(put("/conversations/" + direct + "/read"), baraka, null, "{\"seq\":1}").andExpect(status().isOk())
                .andExpect(jsonPath("$.lastReadSeq").value(1));
        call(get("/conversations"), baraka, null, null).andExpect(jsonPath("$[0].unreadCount").value(0));

        // only its sender changes a message; a deleted one keeps its place, not its content
        call(put("/messages/" + messageId), baraka, null, "{\"body\":\"Changed\"}").andExpect(status().isForbidden());
        call(put("/messages/" + messageId), amani, null, "{\"body\":\"Hello, Baraka\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.editedAt").isNotEmpty());
        call(delete("/messages/" + messageId), amani, null, null).andExpect(status().isNoContent());
        call(get("/conversations/" + direct + "/messages"), baraka, null, null)
                .andExpect(jsonPath("$.messages[0].deleted").value(true)).andExpect(jsonPath("$.messages[0].body").value(nullValue()));

        // somebody else sees nothing, by any route
        String chausiku = oro("oro-chausiku");
        call(get("/conversations/" + direct), chausiku, null, null).andExpect(status().isNotFound());
        call(get("/conversations/" + direct + "/messages"), chausiku, null, null).andExpect(status().isNotFound());
        call(post("/conversations/" + direct + "/messages"), chausiku, null,
                "{\"clientMessageId\":\"client-0002\",\"type\":\"TEXT\",\"body\":\"Hi\"}").andExpect(status().isNotFound());
    }

    @Test
    void theRequestsAreValidated() throws Exception {
        String amani = oro("oro-amani");
        call(get("/me"), amani, ACME.toString(), null).andExpect(status().isBadRequest());
        call(post("/conversations/groups"), amani, null, "{\"name\":\"\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages[0]").value(startsWith("name")));
        String group = idOf(call(post("/conversations/groups"), amani, null, "{\"name\":\"Family\"}").andExpect(status().isCreated()));
        call(post("/conversations/" + group + "/messages"), amani, null, "{\"clientMessageId\":\"short\",\"type\":\"TEXT\",\"body\":\"x\"}")
                .andExpect(status().isBadRequest());
        call(post("/conversations/" + group + "/messages"), amani, null, "{\"clientMessageId\":\"client-0003\",\"type\":\"SYSTEM\",\"body\":\"x\"}")
                .andExpect(status().isBadRequest());
        call(post("/conversations/" + group + "/messages"), amani, null, "{\"clientMessageId\":\"client-0004\",\"type\":\"TEXT\",\"body\":\"x\",\"metadata\":[1]}")
                .andExpect(status().isBadRequest());
        call(get("/conversations/not-a-uuid"), amani, null, null).andExpect(status().isBadRequest());
    }

    @Test
    void umojaRequestsActInAnOrganizationTheUserHasAccessTo() throws Exception {
        String jordan = umoja("c0ffee00-0000-4000-8000-00000000a001");
        String amara = umoja("c0ffee00-0000-4000-8000-00000000a002");
        String bob = umoja("c0ffee00-0000-4000-8000-00000000b001");
        String acme = ACME.toString();

        call(get("/conversations"), jordan, null, null).andExpect(status().isBadRequest());
        call(get("/conversations"), jordan, acme, null).andExpect(status().isForbidden());

        // Umoja's integration feeds the accesses; Oro's cannot, and nobody else
        String jordanAccess = "/internal/organizations/" + acme + "/members/c0ffee00-0000-4000-8000-00000000a001";
        integration(put(jordanAccess), null).andExpect(status().isUnauthorized());
        integration(put(jordanAccess), "a-wrong-integration-key-0123456789-0123456789").andExpect(status().isUnauthorized());
        integration(put(jordanAccess), ORO_KEY).andExpect(status().isBadRequest());
        integration(put(jordanAccess), UMOJA_KEY).andExpect(status().isNoContent());
        integration(put("/internal/organizations/" + acme + "/members/c0ffee00-0000-4000-8000-00000000a002"), UMOJA_KEY)
                .andExpect(status().isNoContent());
        integration(put("/internal/organizations/" + GLOBEX + "/members/c0ffee00-0000-4000-8000-00000000b001"), UMOJA_KEY)
                .andExpect(status().isNoContent());
        // a user token does not open the integration door
        call(put(jordanAccess), jordan, acme, null).andExpect(status().isUnauthorized());

        String design = idOf(call(post("/conversations/groups"), jordan, acme,
                "{\"name\":\"Design\",\"memberUserIds\":[\"c0ffee00-0000-4000-8000-00000000a002\"]}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.context").value("ORGANIZATION"))
                .andExpect(jsonPath("$.organizationId").value(acme)));
        call(post("/conversations/" + design + "/messages"), jordan, acme,
                "{\"clientMessageId\":\"client-0101\",\"type\":\"TEXT\",\"body\":\"Mockups today\"}").andExpect(status().isCreated());
        call(get("/conversations/" + design + "/messages"), amara, acme, null).andExpect(jsonPath("$.messages", hasSize(1)));

        // Bob: no access to Acme, whatever he claims; and he cannot be added there
        call(get("/conversations/" + design + "/messages"), bob, acme, null).andExpect(status().isForbidden());
        call(get("/conversations/" + design + "/messages"), bob, GLOBEX.toString(), null).andExpect(status().isNotFound());
        call(post("/conversations/" + design + "/members"), jordan, acme,
                "{\"userId\":\"c0ffee00-0000-4000-8000-00000000b001\",\"shareHistory\":true}").andExpect(status().isConflict());

        // revoked by Umoja: out of the conversation and of the organization at once
        integration(delete("/internal/organizations/" + acme + "/members/c0ffee00-0000-4000-8000-00000000a002"), UMOJA_KEY)
                .andExpect(status().isNoContent());
        call(get("/conversations/" + design + "/messages"), amara, acme, null).andExpect(status().isForbidden());
    }

    @Test
    void groupMembersAreManagedByTheOwnerWithTheHistoryChoice() throws Exception {
        String amani = oro("oro-amani");
        String baraka = oro("oro-baraka");
        String chausiku = oro("oro-chausiku");
        call(get("/me"), baraka, null, null);
        call(get("/me"), chausiku, null, null);
        String group = idOf(call(post("/conversations/groups"), amani, null, "{\"name\":\"Family\",\"memberUserIds\":[\"oro-baraka\"]}"));
        call(post("/conversations/" + group + "/messages"), amani, null,
                "{\"clientMessageId\":\"client-0201\",\"type\":\"TEXT\",\"body\":\"Before Chausiku\"}");

        call(post("/conversations/" + group + "/members"), baraka, null, "{\"userId\":\"oro-chausiku\",\"shareHistory\":true}")
                .andExpect(status().isForbidden());
        call(post("/conversations/" + group + "/members"), amani, null, "{\"userId\":\"oro-chausiku\",\"shareHistory\":false}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sharedHistory").value(false));
        call(get("/conversations/" + group + "/messages"), chausiku, null, null).andExpect(jsonPath("$.messages", hasSize(0)));
        call(post("/conversations/" + group + "/members"), amani, null, "{\"userId\":\"oro-chausiku\",\"shareHistory\":false}")
                .andExpect(status().isConflict());

        call(delete("/conversations/" + group + "/members/oro-chausiku"), amani, null, null).andExpect(status().isNoContent());
        call(get("/conversations/" + group), chausiku, null, null).andExpect(status().isNotFound());
        // leaving: one's own user id
        call(delete("/conversations/" + group + "/members/oro-baraka"), baraka, null, null).andExpect(status().isNoContent());
        call(get("/conversations/" + group), amani, null, null).andExpect(jsonPath("$.members[*].userId", contains("oro-amani")));
    }

    @Test
    void anIdentityDeactivatedByItsProductIsShutOut() throws Exception {
        String amani = oro("oro-amani");
        call(get("/me"), amani, null, null).andExpect(status().isOk());

        integration(post("/internal/identities/oro-amani/deactivate"), UMOJA_KEY).andExpect(status().isNotFound());
        integration(post("/internal/identities/oro-amani/deactivate"), ORO_KEY).andExpect(status().isNoContent());

        call(get("/me"), amani, null, null).andExpect(status().isForbidden());
        integration(put("/internal/identities/oro-zawadi"), ORO_KEY).andExpect(status().isOk())
                .andExpect(jsonPath("$.product").value("ORO")).andExpect(jsonPath("$.userId").value("oro-zawadi"));
    }
}
