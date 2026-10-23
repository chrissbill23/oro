package com.asata.org.oro.attachment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** runs the purge of the files (oro.attachments.purge.enabled): the attachments every 15 min, the orphans every day */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "oro.attachments.purge", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AttachmentPurgeScheduler {

    private final AttachmentPurge purge;

    @Scheduled(fixedDelayString = "${oro.attachments.purge.interval:PT15M}", initialDelayString = "PT1M")
    public void attachments() {
        purge.purgeAttachments();
    }

    @Scheduled(fixedDelayString = "P1D", initialDelayString = "PT10M")
    public void orphans() {
        purge.purgeOrphans();
    }
}
