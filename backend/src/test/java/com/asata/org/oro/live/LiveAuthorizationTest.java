package com.asata.org.oro.live;

import com.asata.org.oro.PostgresIntegrationTest;
import com.asata.org.oro.context.Actor;
import com.asata.org.oro.enums.Product;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** an open stream stays as authorized as a new request: suspension, deactivation and revocation close it */
class LiveAuthorizationTest extends PostgresIntegrationTest {

    @Autowired
    private LiveHub hub;
    @Autowired
    private LiveAuthorization liveAuthorization;

    private final Instant inTenMinutes = Instant.now().plus(10, ChronoUnit.MINUTES);

    @AfterEach
    void closeStreams() {
        hub.subjects().forEach(subject -> hub.close(subject.identityId()));
    }

    private List<UUID> streaming() {
        return hub.subjects().stream().map(LiveHub.Subject::identityId).toList();
    }

    @Test
    void theStreamsCloseWithTheCommitThatEndsTheirRight() {
        UUID jordan = umojaUser("jordan", ACME, GLOBEX);
        UUID amara = umojaUser("amara", ACME);
        UUID amani = oroUser("amani");
        hub.connect(Actor.umoja(jordan, ACME), "umoja-jordan", inTenMinutes);
        hub.connect(Actor.umoja(jordan, GLOBEX), "umoja-jordan", inTenMinutes);
        hub.connect(Actor.umoja(amara, ACME), "umoja-amara", inTenMinutes);
        hub.connect(Actor.oro(amani), "oro-amani", inTenMinutes);

        // Jordan loses Acme: his Acme stream ends, his Globex one stays
        organizationAccess.revoke(jordan, ACME);
        assertThat(hub.online(Product.UMOJA, ACME)).containsExactly("umoja-amara");
        assertThat(hub.online(Product.UMOJA, GLOBEX)).containsExactly("umoja-jordan");

        // Amara is suspended, Amani deactivated
        identities.suspend(amara);
        identities.deactivate(amani);
        assertThat(streaming()).containsExactly(jordan);
    }

    @Test
    void theCheckClosesWhatChangedElsewhere() {
        UUID jordan = umojaUser("jordan", ACME);
        UUID amara = umojaUser("amara", ACME);
        hub.connect(Actor.umoja(jordan, ACME), "umoja-jordan", inTenMinutes);
        hub.connect(Actor.umoja(amara, ACME), "umoja-amara", inTenMinutes);
        assertThat(liveAuthorization.check()).isZero();

        // changed without the services (a script, another instance): found by the periodic check
        admin.update("UPDATE oro_organization_access SET revoked_at = now() WHERE identity_id = ?", jordan);
        assertThat(liveAuthorization.check()).isEqualTo(1);
        assertThat(hub.online(Product.UMOJA, ACME)).containsExactly("umoja-amara");
    }

    @Test
    void aStreamLastsAsLongAsItsToken() throws Exception {
        UUID amani = oroUser("amani");
        hub.connect(Actor.oro(amani), "oro-amani", Instant.now().plusMillis(200));
        assertThat(streaming()).containsExactly(amani);
        // ended at the expiry of its token, before any check
        Thread.sleep(600);
        assertThat(streaming()).isEmpty();
        assertThat(liveAuthorization.check()).isZero();
    }
}
