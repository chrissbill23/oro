package com.asata.org.oro.account.api;

import com.asata.org.oro.account.AccountKeys;
import com.asata.org.oro.account.AccountProperties;
import com.asata.org.oro.account.AccountService;
import com.asata.org.oro.account.AccountSessions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/**
 * The sign-in of Oro (its people's Asata accounts): the access token in the body, the refresh token in an HttpOnly
 * cookie only for /oro/auth (SameSite Strict: never sent by another site). Public routes, no token.
 */
@Tag(name = "Accounts")
@RestController
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accounts;
    private final AccountProperties properties;
    private final AccountKeys keys;

    public record LoginRequest(@NotNull String login, @NotNull String password, boolean remember) {
    }

    public record SignedInDTO(String accessToken, String tokenType, long expiresIn, UUID userId, String username,
                              boolean mustChangePassword) {
    }

    @Operation(summary = "Sign in", description = "With the email or the username of an Asata account and its password; "
            + "remember: the session lasts 30 days instead of ending with the browser")
    @PostMapping("/auth/login")
    public SignedInDTO login(@Valid @RequestBody LoginRequest login, HttpServletRequest request, HttpServletResponse response) {
        return signedIn(accounts.login(login.login(), login.password(), login.remember(), request.getHeader(HttpHeaders.USER_AGENT)),
                request, response);
    }

    @Operation(summary = "A new access token", description = "From the refresh cookie, which is replaced")
    @PostMapping("/auth/refresh")
    public SignedInDTO refresh(HttpServletRequest request, HttpServletResponse response) {
        return signedIn(accounts.refresh(cookie(request), request.getHeader(HttpHeaders.USER_AGENT)), request, response);
    }

    @Operation(summary = "Sign out", description = "Closes the session of the refresh cookie")
    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        accounts.logout(cookie(request));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(request, "", Duration.ZERO).toString());
    }

    @Operation(summary = "The public keys of Oro's tokens", description = "A JWK set: who checks Oro's access tokens")
    @GetMapping(path = "/.well-known/jwks.json", produces = "application/jwk-set+json")
    public Map<String, Object> jwks() {
        return keys.publicKeys().toJSONObject(true);
    }

    private SignedInDTO signedIn(AccountService.SignedIn signedIn, HttpServletRequest request, HttpServletResponse response) {
        AccountSessions.Session session = signedIn.session();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(request, session.token(), session.remember() ? session.ttl() : null).toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return new SignedInDTO(signedIn.accessToken().value(), "Bearer", signedIn.accessToken().expiresInSeconds(),
                signedIn.userId(), signedIn.username(), signedIn.mustChangePassword());
    }

    private String cookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        return cookies == null ? null : Arrays.stream(cookies).filter(c -> c.getName().equals(properties.getCookieName()))
                .map(Cookie::getValue).findFirst().orElse(null);
    }

    /** maxAge: null for a cookie of the browser's session */
    private ResponseCookie cookie(HttpServletRequest request, String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie.from(properties.getCookieName(), value)
                .httpOnly(true).secure(properties.isCookieSecure()).sameSite("Strict").path(request.getContextPath() + "/auth");
        return (maxAge == null ? cookie : cookie.maxAge(maxAge)).build();
    }
}
