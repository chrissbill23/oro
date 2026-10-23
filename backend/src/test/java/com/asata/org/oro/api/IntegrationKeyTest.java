package com.asata.org.oro.api;

import com.asata.org.oro.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The keys of the integrations, as Oro keeps them: their SHA-256 only, named, several at once for a rotation (the test
 * configuration gives Umoja its current key and the next one). The logs name the key used, never the key.
 */
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class IntegrationKeyTest extends PostgresIntegrationTest {

    private static final String CURRENT = "test-umoja-integration-key-for-the-tests-0123456789";
    private static final String NEXT = "test-umoja-next-integration-key-for-the-tests-0123";
    private static final String ORO = "test-oro-integration-key-for-the-tests-0123456789";

    @Autowired
    private MockMvc mvc;

    private int provision(String key, String userId) throws Exception {
        return mvc.perform(put("/internal/identities/" + userId).header("x-integration-key", key))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void duringARotationBothKeysOpenTheDoorAndTheLogsNameThem(CapturedOutput output) throws Exception {
        assertThat(provision(CURRENT, "c0ffee00-0000-4000-8000-00000000a001")).isEqualTo(200);
        assertThat(provision(NEXT, "c0ffee00-0000-4000-8000-00000000a002")).isEqualTo(200);
        assertThat(count("SELECT count(*) FROM oro_messaging_identity WHERE product_id = 'UMOJA'")).isEqualTo(2);
        // Oro's key acts for Oro only
        assertThat(provision(ORO, "oro-amani")).isEqualTo(200);
        assertThat(count("SELECT count(*) FROM oro_messaging_identity WHERE product_id = 'ORO'")).isEqualTo(1);

        assertThat(output.getAll())
                .contains("integration UMOJA authenticated with its key current", "integration UMOJA authenticated with its key next",
                        "integration ORO authenticated with its key test")
                .doesNotContain(CURRENT, NEXT, ORO);
    }

    @Test
    void aKeyThatIsNotListedIsRefusedAndLoggedWithoutIt(CapturedOutput output) throws Exception {
        String retired = "test-umoja-retired-integration-key-0123456789abcdef";
        assertThat(provision(retired, "c0ffee00-0000-4000-8000-00000000a001")).isEqualTo(401);
        // shorter than a key: refused without even a hash
        assertThat(provision("short-key", "c0ffee00-0000-4000-8000-00000000a001")).isEqualTo(401);
        // the hash Oro keeps is not a key
        assertThat(provision("7a7ef43de33ac9c192f8801d62e3de31a4d5b42e381e0da525c6440f7c28794d",
                "c0ffee00-0000-4000-8000-00000000a001")).isEqualTo(401);
        assertThat(count("SELECT count(*) FROM oro_messaging_identity")).isZero();
        assertThat(output.getAll()).contains("integration key refused on PUT").doesNotContain(retired);
    }
}
