package com.asata.org.oro.account;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The first name of an Asata account (or its username), for the text of a push: the user ids of Oro's and Umoja's
 * identities are Asata accounts' ids. Read through the accounts' login: given_name and username only.
 */
@Component
@RequiredArgsConstructor
public class AccountNames {

    private final AccountDb db;

    /** the name to show of an account; null when unknown (not an Asata account id) */
    public String firstName(String externalUserId) {
        UUID id;
        try {
            id = UUID.fromString(externalUserId);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
        Map<String, String> found = new HashMap<>();
        db.inTransaction(jdbc -> {
            jdbc.query("""
                    SELECT coalesce(nullif(btrim(p.given_name), ''), u.username) AS name
                    FROM asata_user u LEFT JOIN asata_user_personal_info p ON p.user_id = u.id WHERE u.id = ?""",
                    rs -> {
                        found.put("name", rs.getString("name"));
                    }, id);
            return null;
        });
        return found.get("name");
    }
}
