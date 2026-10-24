package com.asata.org.oro.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** the erasures asked, carried out every 30 s (oro.erasure.enabled; the tests run them by hand) */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "oro.erasure", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ErasureScheduler {

    private final ErasureService erasures;

    @Scheduled(fixedDelayString = "${oro.erasure.interval:PT30S}", initialDelayString = "PT20S")
    public void run() {
        erasures.erasePending();
    }
}
