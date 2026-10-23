package com.asata.org.oro.message.service;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the metadata of a message of a user may hold: the clients render it, so nothing a sender could use to mislead
 * them. Only {@code {"mentions": ["<user id>", ...]}}: the user ids (in the product) of members of the conversation,
 * which MessageService checks. Any other key is refused: no link previews from the sender (a preview of one site
 * pointing to another), no system events (only Oro writes those, in system messages).
 */
public final class MessageMetadata {

    public static final int MAX_MENTIONS = 50;
    private static final int MAX_USER_ID_LENGTH = 128;

    private MessageMetadata() {
    }

    /** the mentions of a metadata (none for null), each once; IllegalArgumentException (400) for anything else */
    public static List<String> mentions(JsonNode metadata) {
        if (metadata == null || metadata.isNull()) {
            return List.of();
        }
        if (!metadata.isObject()) {
            throw new IllegalArgumentException("metadata is a JSON object");
        }
        for (String key : metadata.propertyNames()) {
            if (!key.equals("mentions")) {
                throw new IllegalArgumentException("metadata holds mentions only, not " + quoted(key));
            }
        }
        JsonNode list = metadata.get("mentions");
        if (list == null) {
            return List.of();
        }
        if (!list.isArray() || list.size() > MAX_MENTIONS) {
            throw new IllegalArgumentException("mentions is a list of at most " + MAX_MENTIONS + " user ids");
        }
        Set<String> userIds = new LinkedHashSet<>();
        for (JsonNode userId : list) {
            if (!userId.isString() || userId.asString().isBlank() || userId.asString().length() > MAX_USER_ID_LENGTH) {
                throw new IllegalArgumentException("mentions is a list of user ids");
            }
            userIds.add(userId.asString());
        }
        return new ArrayList<>(userIds);
    }

    /** what is stored: the mentions alone, or nothing */
    public static String stored(List<String> mentions, tools.jackson.databind.json.JsonMapper json) {
        return mentions.isEmpty() ? null : json.writeValueAsString(java.util.Map.of("mentions", mentions));
    }

    /** a key of the request in an error message: short, printable */
    private static String quoted(String key) {
        String shown = key.length() > 40 ? key.substring(0, 40) + "…" : key;
        return "\"" + shown.replaceAll("\\p{Cntrl}", "?") + "\"";
    }
}
