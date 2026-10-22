package com.asata.org.oro.outbox.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** oro.outbox.*: the publication of the outbox events */
@Getter
@Setter
@ConfigurationProperties(prefix = "oro.outbox")
public class OutboxProperties {
    /** the poller runs (false in the tests, which run the worker by hand) */
    private boolean enabled = true;
    private Duration pollInterval = Duration.ofSeconds(1);
    private int batchSize = 100;
    /** how long a claimed event is held by its worker; expired, another worker takes it again */
    private Duration lease = Duration.ofSeconds(30);
    /** after the last attempt the event is failed (an alert, replayed by hand) */
    private int maxAttempts = 10;
    /** the wait after the n-th failure: base * 2^(n-1), at most maxBackoff, +-20% of jitter */
    private Duration baseBackoff = Duration.ofSeconds(1);
    private Duration maxBackoff = Duration.ofHours(1);
    /** the processed events are deleted after it */
    private Duration retention = Duration.ofDays(7);
    private Duration purgeInterval = Duration.ofHours(1);
}
