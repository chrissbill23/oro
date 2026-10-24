package com.asata.org.oro.push;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/** oro.push.*: the Web Push notifications of the people away */
@Getter
@Setter
@ConfigurationProperties(prefix = "oro.push")
public class PushProperties {
    private boolean enabled = true;
    /** Oro's VAPID private key (EC P-256, PKCS#8 base64); blank: a new one at each start (development only) */
    private String vapidPrivateKey;
    /** who the push services contact about Oro's pushes: mailto: or https: */
    private String subject = "mailto:ops@oro.localhost";
    /**
     * the push services a subscription may name (its host, or a host ending with .host): Oro posts to the endpoint a
     * browser gives, so never anywhere else
     */
    private List<String> allowedHosts = List.of("fcm.googleapis.com", "updates.push.services.mozilla.com",
            "push.services.mozilla.com", "push.apple.com", "notify.windows.com");
    /** http endpoints too: the tests only (ProductionChecks refuses it) */
    private boolean allowHttp = false;
    /** how long a push service keeps a push for a device that is off */
    private Duration ttl = Duration.ofHours(24);
    private Duration timeout = Duration.ofSeconds(10);
    /** the subscriptions of one identity at most (its browsers): the oldest go beyond */
    private int maxSubscriptions = 10;
}
