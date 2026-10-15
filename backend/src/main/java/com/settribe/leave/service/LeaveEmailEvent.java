package com.settribe.leave.service;

import com.settribe.leave.entity.LeaveType;

import java.time.LocalDate;

/**
 * Snapshot of a leave event taken inside the workflow transaction, so email delivery never touches JPA entities.
 */
public record LeaveEmailEvent(
        Kind kind,
        String employeeName,
        String employeeEmail,
        String managerEmail,
        LeaveType leaveType,
        LocalDate fromDate,
        LocalDate toDate,
        String remarks) {

    public enum Kind {
        SUBMITTED, APPROVED, REJECTED, CANCELLED
    }
}
