package com.asata.org.oro.config.metrics;

import com.asata.org.oro.attachment.storage.FileStorage;
import com.asata.org.oro.context.DbSession;
import com.asata.org.oro.live.LiveHub;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The gauges of Oro, for Prometheus and its alerts (monitoring/rules.yml). What the database knows is read every 15 s,
 * as oro_system, one query each, and kept: a scrape never queries the database.
 * <ul>
 * <li>oro_outbox_pending, oro_outbox_failed, oro_outbox_oldest_pending_seconds: the events to publish, the ones that
 * failed for good, the age of the oldest waiting (the lag of the live events)</li>
 * <li>oro_attachments_scan_pending, oro_attachments_scan_oldest_pending_seconds: the files waiting for their scan</li>
 * <li>oro_wal_archive_age_seconds, oro_wal_archive_failed: the point-in-time recovery (pg_stat_archiver)</li>
 * <li>oro_erasures_pending, oro_erasures_oldest_pending_seconds: the erasures of people asked and not done</li>
 * <li>oro_streams_open, oro_attachments_disk_free_bytes, oro_files_backup_age_seconds: read when scraped</li>
 * <li>oro_restore_check_age_seconds{what=database|files}: since the weekly restore check last succeeded</li>
 * </ul>
 * NaN when not known (yet).
 */
@Slf4j
@Component
public class OroMetrics {

    private final DbSession session;
    private final AtomicReference<Map<String, Double>> database = new AtomicReference<>(Map.of());
    private final String filesBackupStatus;

    public OroMetrics(MeterRegistry registry, DbSession session, LiveHub hub, ObjectProvider<FileStorage> storage,
                      @Value("${oro.monitoring.files-backup-status:}") String filesBackupStatus,
                      @Value("${oro.monitoring.files-check-status:}") String filesCheckStatus,
                      @Value("${oro.monitoring.database-check-status:}") String databaseCheckStatus) {
        this.session = session;
        this.filesBackupStatus = filesBackupStatus;
        for (String name : new String[]{"outbox.pending", "outbox.failed", "outbox.oldest.pending.seconds",
                "attachments.scan.pending", "attachments.scan.oldest.pending.seconds", "wal.archive.age.seconds",
                "wal.archive.failed", "erasures.pending", "erasures.oldest.pending.seconds"}) {
            Gauge.builder("oro." + name, database, values -> values.get().getOrDefault(name, Double.NaN)).register(registry);
        }
        Gauge.builder("oro.streams.open", hub, LiveHub::openStreams).description("streams open on this instance").register(registry);
        io.micrometer.core.instrument.FunctionCounter.builder("oro.streams.ended.slow", hub, LiveHub::streamsEndedSlow)
                .description("streams ended because their client read too slowly").register(registry);
        Gauge.builder("oro.attachments.disk.free.bytes", storage, files -> {
            FileStorage local = files.getIfAvailable();
            long usable = local == null ? -1 : local.usableSpace();
            return usable < 0 ? Double.NaN : usable;
        }).register(registry);
        Gauge.builder("oro.files.backup.age.seconds", this, OroMetrics::filesBackupAge).register(registry);
        // the restore checks of the backups (weekly): the database's (its service restore-check), the files'
        Gauge.builder("oro.restore.check.age.seconds", databaseCheckStatus, OroMetrics::ageOf).tag("what", "database")
                .description("since the backups of the database last restored").register(registry);
        Gauge.builder("oro.restore.check.age.seconds", filesCheckStatus, OroMetrics::ageOf).tag("what", "files")
                .description("since the backups of the files last checked").register(registry);
    }

    @Scheduled(fixedDelayString = "${oro.monitoring.refresh:PT15S}", initialDelayString = "PT5S")
    public void refresh() {
        try {
            Map<String, Double> values = session.systemTransaction().execute(status -> {
                session.enterSystem();
                Map<String, Object> outbox = session.jdbc().queryForMap("""
                        SELECT count(*) FILTER (WHERE status = 'pending') AS pending,
                               count(*) FILTER (WHERE status = 'failed') AS failed,
                               coalesce(extract(epoch FROM now() - min(created_at) FILTER (WHERE status IN ('pending', 'processing'))), 0) AS oldest
                        FROM oro_outbox_event WHERE status <> 'processed'""");
                Map<String, Object> scans = session.jdbc().queryForMap("""
                        SELECT count(*) AS pending, coalesce(extract(epoch FROM now() - min(created_at)), 0) AS oldest
                        FROM oro_attachment WHERE upload_status = 'uploaded' AND scan_status = 'pending' AND deleted_at IS NULL""");
                Map<String, Object> erasures = session.jdbc().queryForMap("""
                        SELECT count(*) AS pending, coalesce(extract(epoch FROM now() - min(requested_at)), 0) AS oldest
                        FROM oro_erasure WHERE completed_at IS NULL""");
                Map<String, Object> archiver = session.jdbc().queryForMap("""
                        SELECT extract(epoch FROM now() - last_archived_time) AS age, failed_count AS failed,
                               last_failed_time > coalesce(last_archived_time, '-infinity') AS failing
                        FROM pg_stat_archiver""");
                return Map.of(
                        "outbox.pending", number(outbox.get("pending")),
                        "outbox.failed", number(outbox.get("failed")),
                        "outbox.oldest.pending.seconds", number(outbox.get("oldest")),
                        "attachments.scan.pending", number(scans.get("pending")),
                        "attachments.scan.oldest.pending.seconds", number(scans.get("oldest")),
                        "erasures.pending", number(erasures.get("pending")),
                        "erasures.oldest.pending.seconds", number(erasures.get("oldest")),
                        "wal.archive.age.seconds", number(archiver.get("age")),
                        // failing now: the last attempt failed after the last success
                        "wal.archive.failed", Boolean.TRUE.equals(archiver.get("failing")) ? 1.0 : 0.0);
            });
            database.set(values == null ? Map.of() : values);
        } catch (RuntimeException e) {
            // unknown, not zero: the alerts on absent values fire
            database.set(Map.of());
            log.warn("metrics of the database not read: {}", e.getClass().getSimpleName());
        }
    }

    /** seconds since the backup of the files last succeeded (the time its loop writes), NaN when unknown */
    double filesBackupAge() {
        return ageOf(filesBackupStatus);
    }

    /** seconds since the time (epoch seconds) a file holds: a backup or a check written by its loop; NaN when unknown */
    static double ageOf(String statusFile) {
        if (statusFile == null || statusFile.isBlank()) {
            return Double.NaN;
        }
        try {
            long at = Long.parseLong(Files.readString(Path.of(statusFile)).strip());
            return Instant.now().getEpochSecond() - at;
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    private static double number(Object value) {
        return value == null ? Double.NaN : ((Number) value).doubleValue();
    }
}
