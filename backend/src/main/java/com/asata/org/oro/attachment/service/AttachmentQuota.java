package com.asata.org.oro.attachment.service;

import com.asata.org.oro.context.DbSession;
import com.asata.org.oro.exception.PayloadTooLarge;
import com.asata.org.oro.exception.TooManyRequests;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * What a user may upload (oro.attachments.quota.*), checked before a file is received into the storage: the bytes of
 * its files that are not deleted (wherever they are: oro_system sees the conversations the user left too), and the files
 * of the last 24 hours (deleted ones included: deleting does not give uploads back). Two uploads at the same moment may
 * pass together: the quota is a bound, not an exact count.
 */
@Service
public class AttachmentQuota {

    private final DbSession session;
    private final long bytesPerUser;
    private final int filesPerDay;

    public AttachmentQuota(DbSession session,
                           @Value("${oro.attachments.quota.bytes-per-user:1073741824}") long bytesPerUser,
                           @Value("${oro.attachments.quota.files-per-day:200}") int filesPerDay) {
        this.session = session;
        this.bytesPerUser = bytesPerUser;
        this.filesPerDay = filesPerDay;
    }

    /** refused: PayloadTooLarge (the bytes), TooManyRequests (the files of the day) */
    @Transactional(transactionManager = DbSession.SYSTEM_TRANSACTION_MANAGER, readOnly = true)
    public void check(UUID identityId, long sizeBytes) {
        session.enterSystem();
        Map<String, Object> used = session.jdbc().queryForMap("""
                SELECT coalesce(sum(size_bytes) FILTER (WHERE deleted_at IS NULL), 0) AS bytes,
                       count(*) FILTER (WHERE created_at > now() - interval '1 day') AS today
                FROM oro_attachment WHERE uploaded_by = ?""", identityId);
        if (((Number) used.get("today")).longValue() >= filesPerDay) {
            throw new TooManyRequests("Too many files today (" + filesPerDay + ")", 3600);
        }
        if (((Number) used.get("bytes")).longValue() + sizeBytes > bytesPerUser) {
            throw new PayloadTooLarge("The files of this user would go over their storage quota (" + bytesPerUser + " bytes)");
        }
    }
}
