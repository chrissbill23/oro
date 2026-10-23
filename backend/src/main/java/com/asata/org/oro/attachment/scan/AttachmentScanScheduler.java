package com.asata.org.oro.attachment.scan;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** asks the scanner again, every minute, for the files it could not scan when they came */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "oro.attachments", name = "scan", havingValue = "clamav")
public class AttachmentScanScheduler {

    private final AttachmentScanning scanning;

    @Scheduled(fixedDelayString = "${oro.attachments.clamav.retry-interval:PT1M}", initialDelayString = "PT30S")
    public void scanPending() {
        scanning.scanPending();
    }
}
