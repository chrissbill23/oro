package com.asata.org.oro.config.ratelimit;

import com.asata.org.oro.exception.TooManyRequests;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UrlPathHelper;

import java.util.List;

/**
 * The rate limits of the users, before any work of the request (no database yet): every request of a user takes from
 * its "any" bucket, and the costly or noisy ones from their own bucket too. The user is the one of its token (issuer and
 * subject): limited even before Oro knows its identity. Over a limit: 429 and Retry-After. The integrations (/internal,
 * trusted) are not limited here. The public routes of the accounts (no token) are limited per client address (the
 * proxy's X-Forwarded-For: only the client, nginx sets it), against guessing passwords across accounts and flooding
 * mailboxes; the proxy limits the rest.
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    static final String ANY = "any";

    /** a kind of request: its method and path (within /oro) */
    private record Rule(HttpMethod method, String pattern, String kind) {
    }

    /** the first that matches decides (the reactions before the other changes of a message) */
    private static final List<Rule> RULES = List.of(
            new Rule(HttpMethod.POST, "/conversations/*/messages", "send"),
            new Rule(HttpMethod.PUT, "/messages/*/reactions/*", "react"),
            new Rule(HttpMethod.DELETE, "/messages/*/reactions/*", "react"),
            new Rule(HttpMethod.PUT, "/messages/*", "change-message"),
            new Rule(HttpMethod.DELETE, "/messages/*", "change-message"),
            new Rule(HttpMethod.POST, "/conversations/*/typing", "typing"),
            new Rule(HttpMethod.GET, "/search/messages", "search"),
            new Rule(HttpMethod.POST, "/conversations/direct", "open-direct"),
            new Rule(HttpMethod.POST, "/conversations/groups", "create-group"),
            new Rule(HttpMethod.POST, "/conversations/*/members", "add-member"),
            new Rule(HttpMethod.POST, "/conversations/*/attachments", "upload"),
            new Rule(HttpMethod.GET, "/stream", "stream"),
            new Rule(HttpMethod.POST, "/me/erase", "erase"),
            new Rule(HttpMethod.GET, "/me/export", "export"),
            new Rule(HttpMethod.POST, "/reports", "report"),
            new Rule(HttpMethod.PUT, "/blocks/*", "block"),
            new Rule(HttpMethod.PUT, "/push/subscriptions", "push-subscribe"));

    /** the public routes of the accounts, per client address */
    private static final List<Rule> ANONYMOUS_RULES = List.of(
            new Rule(HttpMethod.POST, "/auth/login", "login"),
            new Rule(HttpMethod.POST, "/auth/refresh", "refresh"),
            new Rule(HttpMethod.POST, "/auth/signup", "signup"),
            new Rule(HttpMethod.POST, "/auth/verify-email/resend", "email"),
            new Rule(HttpMethod.POST, "/auth/password/forgot", "email"),
            new Rule(HttpMethod.POST, "/auth/verify-email", "link"),
            new Rule(HttpMethod.POST, "/auth/password/reset", "link"));

    private static final AntPathMatcher PATHS = new AntPathMatcher();
    private static final UrlPathHelper URL_PATHS = new UrlPathHelper();

    private final RateLimiter limiter;
    private final RateLimitProperties properties;
    private final MeterRegistry meters;

    public RateLimitInterceptor(RateLimiter limiter, RateLimitProperties properties, MeterRegistry meters) {
        this.limiter = limiter;
        this.properties = properties;
        this.meters = meters;
        if (properties.isEnabled() && !properties.getLimits().containsKey(ANY)) {
            throw new IllegalStateException("oro.rate-limits.limits.any: the limit of every request of a user");
        }
        properties.getLimits().forEach((kind, limit) -> {
            if (limit.getPerMinute() < 1 || limit.getBurst() < 1) {
                throw new IllegalStateException("oro.rate-limits.limits." + kind + ": per-minute and burst of 1 at least");
            }
        });
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // a request counts once: not the dispatches that end a stream or an error
        if (!properties.isEnabled() || request.getDispatcherType() != DispatcherType.REQUEST) {
            return true;
        }
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            String path = URL_PATHS.getPathWithinApplication(request);
            for (Rule rule : ANONYMOUS_RULES) {
                if (rule.method().matches(request.getMethod()) && PATHS.match(rule.pattern(), path)) {
                    take("address/" + request.getRemoteAddr(), rule.kind());
                    break;
                }
            }
            return true;
        }
        String caller = token.getToken().getClaimAsString("iss") + "/" + token.getToken().getSubject();
        take(caller, ANY);
        String path = URL_PATHS.getPathWithinApplication(request);
        for (Rule rule : RULES) {
            if (rule.method().matches(request.getMethod()) && PATHS.match(rule.pattern(), path)) {
                take(caller, rule.kind());
                break;
            }
        }
        return true;
    }

    private void take(String caller, String kind) {
        RateLimitProperties.Limit limit = properties.getLimits().get(kind);
        if (limit == null) {
            return;
        }
        long retryAfter = limiter.acquire(caller, kind, limit);
        if (retryAfter > 0) {
            meters.counter("oro.rate.limit.refused", "kind", kind).increment();
            log.info("rate limit {} reached by {}", kind, caller);
            throw new TooManyRequests("Too many requests (" + kind + "), try again in " + retryAfter + " s", retryAfter);
        }
    }
}
