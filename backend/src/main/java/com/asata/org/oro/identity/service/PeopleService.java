package com.asata.org.oro.identity.service;

import com.asata.org.oro.account.AccountNames;
import com.asata.org.oro.context.Actor;
import com.asata.org.oro.context.DbSession;
import com.asata.org.oro.enums.Product;
import com.asata.org.oro.exception.Forbidden;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The names of the people an Oro person may see named: itself, and the people of its conversations (members now or
 * before: their old messages are still there; a request's sender too). Not anyone else's: a user id alone names
 * nobody. The names are the Asata account's (given name, surname, username). Umoja's people get theirs from Umoja.
 */
@Service
@RequiredArgsConstructor
public class PeopleService {

    /** the most user ids of one call */
    public static final int MAX = 200;

    private final DbSession session;
    private final AccountNames names;

    public record Person(String userId, String givenName, String surname, String username) {
    }

    public List<Person> people(Actor actor, Collection<String> userIds) {
        if (actor.product() != Product.ORO) {
            throw new Forbidden("Umoja's people are named by Umoja");
        }
        Set<String> asked = new LinkedHashSet<>(userIds);
        if (asked.size() > MAX) {
            throw new IllegalArgumentException("At most " + MAX + " user ids at once");
        }
        Set<UUID> known = new HashSet<>();
        for (String userId : asked) {
            try {
                known.add(UUID.fromString(userId));
            } catch (IllegalArgumentException e) {
                // an erased person ("erased-…") or not an account: no name
            }
        }
        if (known.isEmpty()) {
            return List.of();
        }
        Set<String> visible = new HashSet<>();
        session.systemTransaction().executeWithoutResult(status -> {
            session.enterSystem();
            Array ids = session.jdbc().execute((java.sql.Connection connection) ->
                    connection.createArrayOf("varchar", known.stream().map(UUID::toString).toArray(String[]::new)));
            visible.addAll(session.jdbc().queryForList("""
                    SELECT i.external_user_id FROM oro_messaging_identity i
                    WHERE i.product_id = 'ORO' AND i.external_user_id = ANY(?)
                      AND (i.id = ? OR EXISTS (
                          SELECT 1 FROM oro_conversation_member mine
                          JOIN oro_conversation_member theirs ON theirs.conversation_id = mine.conversation_id
                          WHERE mine.user_id = ? AND mine.left_at IS NULL AND theirs.user_id = i.id))""",
                    String.class, ids, actor.identityId(), actor.identityId()));
        });
        Map<UUID, AccountNames.Name> found = names.names(visible.stream().map(UUID::fromString).toList());
        List<Person> people = new ArrayList<>();
        for (String userId : asked) {
            if (visible.contains(userId)) {
                AccountNames.Name name = found.get(UUID.fromString(userId));
                if (name != null) {
                    people.add(new Person(userId, name.givenName(), name.surname(), name.username()));
                }
            }
        }
        return people;
    }
}
