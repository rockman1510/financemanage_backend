package com.financemanage.identity.infrastructure.scheduler;

import com.financemanage.identity.domain.LoginChallengeRepository;
import com.financemanage.identity.domain.RefreshTokenRepository;
import com.financemanage.identity.domain.VerificationCodeRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily job: removes expired refresh tokens, revoked ones older than a week, and codes and sign-in challenges
 * expired for a day.
 */
@Component
class IdentityCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(IdentityCleanupJob.class);

    private final RefreshTokenRepository refreshTokens;
    private final VerificationCodeRepository codes;
    private final LoginChallengeRepository challenges;
    private final Clock clock;

    IdentityCleanupJob(RefreshTokenRepository refreshTokens, VerificationCodeRepository codes,
                       LoginChallengeRepository challenges, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.codes = codes;
        this.challenges = challenges;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.identity.cleanup-cron:0 45 3 * * *}")
    @SchedulerLock(name = "identity-cleanup", lockAtMostFor = "PT10M")
    @Transactional
    public void run() {
        Instant now = clock.instant();
        Instant dayAgo = now.minus(Duration.ofDays(1));
        int tokens = refreshTokens.deleteRevokedOrExpired(now, now.minus(Duration.ofDays(7)));
        int removedCodes = codes.deleteExpiredBefore(dayAgo);
        int removedChallenges = challenges.deleteExpiredBefore(dayAgo);
        log.info("Identity cleanup: {} refresh tokens, {} codes, {} challenges removed", tokens, removedCodes,
                removedChallenges);
    }
}
