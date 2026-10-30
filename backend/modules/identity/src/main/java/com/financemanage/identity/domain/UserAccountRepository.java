package com.financemanage.identity.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<UserAccount> findByGoogleSubject(String googleSubject);
}
