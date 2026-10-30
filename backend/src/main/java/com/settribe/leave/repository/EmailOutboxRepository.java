package com.settribe.leave.repository;

import com.settribe.leave.entity.EmailOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

    boolean existsByEventKey(String eventKey);

    List<EmailOutbox> findTop20ByStatusInAndAvailableAtBeforeOrderByIdAsc(List<String> statuses,
                                                                          LocalDateTime availableAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EmailOutbox e SET e.status = 'PROCESSING', e.attempts = e.attempts + 1, "
            + "e.availableAt = :leaseUntil WHERE e.id = :id AND e.status IN ('PENDING', 'RETRY')")
    int claim(@Param("id") Long id, @Param("leaseUntil") LocalDateTime leaseUntil);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EmailOutbox e SET e.status = 'RETRY', e.availableAt = :now "
            + "WHERE e.status = 'PROCESSING' AND e.availableAt < :staleBefore")
    int releaseStale(@Param("now") LocalDateTime now, @Param("staleBefore") LocalDateTime staleBefore);
}
