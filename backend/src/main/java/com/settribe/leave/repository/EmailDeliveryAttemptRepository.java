package com.settribe.leave.repository;

import com.settribe.leave.entity.EmailDeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailDeliveryAttemptRepository extends JpaRepository<EmailDeliveryAttempt, Long> {
}
