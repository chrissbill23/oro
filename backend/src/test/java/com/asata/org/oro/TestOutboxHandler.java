package com.asata.org.oro;

import com.asata.org.oro.outbox.service.ClaimedEvent;
import com.asata.org.oro.outbox.service.OutboxEventHandler;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** the publisher of the tests: records the events, or fails as a test tells it */
@Primary
@TestComponent
public class TestOutboxHandler implements OutboxEventHandler {

    private final List<ClaimedEvent> handled = new CopyOnWriteArrayList<>();
    private volatile Consumer<ClaimedEvent> behavior = event -> { };

    @Override
    public void handle(ClaimedEvent event) {
        behavior.accept(event);
        handled.add(event);
    }

    public List<ClaimedEvent> handled() {
        return handled;
    }

    public void failWith(RuntimeException exception) {
        behavior = event -> {
            throw exception;
        };
    }

    public void reset() {
        handled.clear();
        behavior = event -> { };
    }
}
