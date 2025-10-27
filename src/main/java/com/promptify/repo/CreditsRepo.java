package com.promptify.repo;
import com.promptify.entity.*;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CreditsRepo extends JpaRepository<Credits, Long> {
    Optional<Credits> findByUser(User user);

	Credits findByUserId(Long userId);
}
