package com.asata.org.oro.api;

import com.asata.org.oro.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The HTTP routes of the messaging page of Umoja: the synchronization of an organization by its integration, the list,
 * reactions, search, mute, the files (upload, then download with safe headers).
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "oro.attachments.max-size-bytes=1000")
class MessagingApiTest extends PostgresIntegrationTest {

    private static final String UMOJA_SECRET = "test-umoja-jwt-secret-for-the-tests-only-0123456789";
    private static final String UMOJA_KEY = "test-umoja-integration-key-for-the-tests-0123456789";
    private static final String TEAM = "77777777-0000-4000-8000-000000000001";

    @Autowired
    private MockMvc mvc;

    private String jordan;
    private String amara;
    private String kwame;

    private static String umoja(String userId) {
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                UMOJA_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("umoja").subject(userId).issuedAt(now)
                .expiresAt(now.plus(10, ChronoUnit.MINUTES)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).header("x-organization-id", ACME.toString());
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(request);
    }

    private long version;

    /** the snapshot with the next version, as Umoja numbers them */
    private ResultActions sync(String json) throws Exception {
        String versioned = json.replaceFirst("\\{", "{\"version\": " + (++version) + ", ");
        return mvc.perform(put("/internal/organizations/" + ACME).header("x-integration-key", UMOJA_KEY)
                .contentType(MediaType.APPLICATION_JSON).content(versioned));
    }

    private static String read(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path).toString();
    }

    @BeforeEach
    void acme() throws Exception {
        jordan = umoja("umoja-jordan");
        amara = umoja("umoja-amara");
        kwame = umoja("umoja-kwame");
        sync("""
                {"memberUserIds": ["umoja-jordan", "umoja-amara", "umoja-kwame"],
                 "teams": [{"teamId": "%s", "name": "Design", "memberUserIds": ["umoja-jordan", "umoja-amara"]}]}"""
                .formatted(TEAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granted").value(3))
                .andExpect(jsonPath("$.chatsCreated").value(1));
    }

    @Test
    void onlyUmojaSynchronizesItsOrganizations() throws Exception {
        mvc.perform(put("/internal/organizations/" + ACME).header("x-integration-key",
                        "test-oro-integration-key-for-the-tests-0123456789")
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\": 99, \"memberUserIds\": [], \"teams\": []}"))
                .andExpect(status().isBadRequest());
        // a snapshot without its version is refused
        mvc.perform(put("/internal/organizations/" + ACME).header("x-integration-key", UMOJA_KEY)
                .contentType(MediaType.APPLICATION_JSON).content("{\"memberUserIds\": [], \"teams\": []}"))
                .andExpect(status().isBadRequest());
        // a user token is no key: that door knows only the integrations
        call(put("/internal/organizations/" + ACME), jordan, "{\"memberUserIds\": [], \"teams\": []}")
                .andExpect(status().isUnauthorized());
        sync("{\"memberUserIds\": [\"not an id\"], \"teams\": []}").andExpect(status().isConflict());
    }

    @Test
    void aTeamChatIsListedReactedToSearchedAndMuted() throws Exception {
        String chat = read(call(get("/conversations"), jordan, null)
                .andExpect(jsonPath("$[0].teamId").value(TEAM))
                .andExpect(jsonPath("$[0].name").value("Design"))
                .andExpect(jsonPath("$[0].memberCount").value(2)), "$[0].id");
        call(put("/conversations/" + chat), jordan, "{\"name\": \"Mine\"}").andExpect(status().isConflict());

        // a client that sends no metadata as null
        call(post("/conversations/" + chat + "/messages"), amara,
                "{\"clientMessageId\": \"client-0000\", \"type\": \"TEXT\", \"body\": \"Hello\", \"metadata\": null}")
                .andExpect(status().isCreated());
        call(post("/conversations/" + chat + "/messages"), amara,
                "{\"clientMessageId\": \"client-000x\", \"type\": \"TEXT\", \"body\": \"Hello\", \"metadata\": [1]}")
                .andExpect(status().isBadRequest());
        String message = read(call(post("/conversations/" + chat + "/messages"), jordan,
                "{\"clientMessageId\": \"client-0001\", \"type\": \"TEXT\", \"body\": \"The onboarding research\", "
                        + "\"metadata\": {\"mentions\": [\"umoja-amara\"]}}")
                .andExpect(status().isCreated()), "$.id");
        call(get("/conversations"), amara, null)
                .andExpect(jsonPath("$[0].unreadCount").value(1))
                .andExpect(jsonPath("$[0].lastMessage.body").value("The onboarding research"))
                .andExpect(jsonPath("$[0].lastMessage.senderUserId").value("umoja-jordan"));

        call(put("/messages/" + message + "/reactions/👍"), amara, null)
                .andExpect(jsonPath("$.reactions[0].emoji").value("👍"))
                .andExpect(jsonPath("$.reactions[0].userIds", contains("umoja-amara")));
        call(get("/messages/" + message), jordan, null)
                .andExpect(jsonPath("$.metadata.mentions", contains("umoja-amara")))
                .andExpect(jsonPath("$.reactions[0].userIds", contains("umoja-amara")));
        call(delete("/messages/" + message + "/reactions/👍"), amara, null).andExpect(jsonPath("$.reactions", hasSize(0)));
        call(get("/messages/" + message), kwame, null).andExpect(status().isNotFound());

        call(get("/search/messages").param("q", "onboarding"), amara, null)
                .andExpect(jsonPath("$[*].id", contains(message)));
        call(get("/search/messages").param("q", "onboarding"), kwame, null).andExpect(jsonPath("$", hasSize(0)));

        call(put("/conversations/" + chat + "/mute"), amara, "{\"until\": \"2099-01-01T00:00:00Z\"}")
                .andExpect(jsonPath("$.mutedUntil").isNotEmpty())
                .andExpect(jsonPath("$.teamId").value(TEAM))
                .andExpect(jsonPath("$.members[*].userId", containsInAnyOrder("umoja-jordan", "umoja-amara")));
        call(put("/conversations/" + chat + "/read"), amara, "{\"seq\": 2}").andExpect(status().isOk());
        call(get("/conversations/" + chat), jordan, null)
                .andExpect(jsonPath("$.members[?(@.userId == 'umoja-amara')].lastReadSeq", contains(2)))
                .andExpect(jsonPath("$.members[?(@.userId == 'umoja-amara')].lastReadAt", contains(notNullValue())));
        call(post("/conversations/" + chat + "/typing"), amara, null).andExpect(status().isNoContent());
    }

    @Test
    void aFileIsUploadedSentAndDownloadedByTheMembersOnly() throws Exception {
        String direct = read(call(post("/conversations/direct"), jordan, "{\"userId\": \"umoja-amara\"}"), "$.id");
        MockMultipartFile pdf = new MockMultipartFile("file", "../Onboarding synthesis.pdf", "application/pdf",
                "%PDF-1.4 a small file".getBytes(StandardCharsets.UTF_8));
        String file = read(mvc.perform(multipart("/conversations/" + direct + "/attachments").file(pdf)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jordan).header("x-organization-id", ACME.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("Onboarding synthesis.pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(21))
                .andExpect(jsonPath("$.available").value(true)), "$.id");

        // not sent yet: its uploader only
        call(get("/attachments/" + file + "/content"), amara, null).andExpect(status().isNotFound());
        call(post("/conversations/" + direct + "/messages"), jordan,
                "{\"clientMessageId\": \"client-0002\", \"type\": \"FILE\", \"attachmentIds\": [\"%s\"]}".formatted(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments[0].fileName").value("Onboarding synthesis.pdf"));

        call(get("/attachments/" + file + "/content"), amara, null)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/octet-stream"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment;")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().string("%PDF-1.4 a small file"));
        call(get("/attachments/" + file + "/content"), kwame, null).andExpect(status().isNotFound());

        // too big (1000 bytes in this test)
        mvc.perform(multipart("/conversations/" + direct + "/attachments")
                        .file(new MockMultipartFile("file", "big.bin", "application/octet-stream", new byte[1001]))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jordan).header("x-organization-id", ACME.toString()))
                .andExpect(status().isBadRequest());
    }
}
