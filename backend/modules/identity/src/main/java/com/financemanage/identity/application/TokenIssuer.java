package com.financemanage.identity.application;

import com.financemanage.identity.domain.UserAccount;

/** Outbound port for signing access tokens. */
public interface TokenIssuer {

    String issueAccessToken(UserAccount user);

    long accessTokenTtlSeconds();
}
