package com.asata.org.oro.identity.repository;

import com.asata.org.oro.enums.Product;
import com.asata.org.oro.identity.entity.MessagingIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessagingIdentityRepository extends JpaRepository<MessagingIdentity, UUID> {

    /** identity resolution, served by uq_oro_messaging_identity_external */
    Optional<MessagingIdentity> findByProductAndExternalUserId(Product product, String externalUserId);
}
