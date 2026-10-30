package com.financemanage.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.financemanage.identity.api.IdentityEvents.UserRegistered;
import com.financemanage.identity.domain.RefreshToken;
import com.financemanage.identity.domain.RefreshTokenRepository;
import com.financemanage.identity.domain.UserAccount;
import com.financemanage.identity.domain.UserAccountRepository;
import com.financemanage.shared.domain.DomainException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final Map<String, RefreshToken> tokenStore = new HashMap<>();
    private final List<Object> events = new ArrayList<>();
    private final Clock clock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
    private AuthService service;

    /** Reversible fake encoder keeps the test fast and deterministic. */
    private final PasswordEncoder encoder = new PasswordEncoder() {
        @Override
        public String encode(CharSequence raw) {
            return "hashed:" + raw;
        }

        @Override
        public boolean matches(CharSequence raw, String encoded) {
            return encoded.equals("hashed:" + raw);
        }
    };

    private final TokenIssuer issuer = new TokenIssuer() {
        @Override
        public String issueAccessToken(UserAccount user) {
            return "access-" + user.getEmail();
        }

        @Override
        public long accessTokenTtlSeconds() {
            return 900;
        }
    };

    @BeforeEach
    void setUp() {
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokens.save(any())).thenAnswer(inv -> {
            RefreshToken token = inv.getArgument(0);
            tokenStore.put(token.getTokenHash(), token);
            return token;
        });
        when(refreshTokens.findByTokenHash(any()))
                .thenAnswer(inv -> Optional.ofNullable(tokenStore.get(inv.<String>getArgument(0))));
        service = new AuthService(users, refreshTokens, encoder, issuer, events::add, clock, Duration.ofDays(30));
    }

    @Test
    void registerNormalizesEmailAndIssuesTokens() {
        TokenPair tokens = service.register("  An@Example.com ", "secret123", "Nguyễn An");

        assertThat(tokens.accessToken()).isEqualTo("access-an@example.com");
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.expiresInSeconds()).isEqualTo(900);
        assertThat(tokenStore).hasSize(1);
        assertThat(events).singleElement().isInstanceOfSatisfying(UserRegistered.class,
                e -> assertThat(e.email()).isEqualTo("an@example.com"));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(users.existsByEmail("an@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register("AN@example.com", "secret123", "An"))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("EMAIL_TAKEN");
    }

    @Test
    void loginChecksPassword() {
        UserAccount user = new UserAccount("an@example.com", "hashed:secret123", "An");
        when(users.findByEmail("an@example.com")).thenReturn(Optional.of(user));

        assertThat(service.login("an@example.com", "secret123").accessToken()).isEqualTo("access-an@example.com");
        assertThatThrownBy(() -> service.login("an@example.com", "wrong"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refreshRotatesTokenAndRejectsReuse() {
        UserAccount user = new UserAccount("an@example.com", "hashed:secret123", "An");
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(users.findByEmail("an@example.com")).thenReturn(Optional.of(user));
        String original = service.login("an@example.com", "secret123").refreshToken();

        TokenPair rotated = service.refresh(original);

        assertThat(rotated.refreshToken()).isNotEqualTo(original);
        assertThatThrownBy(() -> service.refresh(original)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void logoutRevokesRefreshToken() {
        UserAccount user = new UserAccount("an@example.com", "hashed:secret123", "An");
        when(users.findByEmail("an@example.com")).thenReturn(Optional.of(user));
        String refresh = service.login("an@example.com", "secret123").refreshToken();

        service.logout(refresh);

        assertThat(tokenStore.get(AuthService.hash(refresh)).isRevoked()).isTrue();
    }
}
