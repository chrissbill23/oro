package com.asata.org.oro.outbox.repository;

import com.asata.org.oro.outbox.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** the API inserts only (oro_app has no SELECT on the outbox); the workers use OutboxWorker */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
}
