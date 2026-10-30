package com.financemanage.identity.application;

import com.financemanage.identity.domain.UserAccount;
import java.util.UUID;

/** Outbound port for signing access tokens. */
public interface TokenIssuer {

    /** Access token for the user; {@code sessionId} identifies the signed-in device ({@code sid} claim). */
    String issueAccessToken(UserAccount user, UUID sessionId);

    long accessTokenTtlSeconds();
}
