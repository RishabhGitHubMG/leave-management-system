package com.settribe.leave.entity;

/**
 * Status values representing the lifecycle state of a leave request:
 * - PENDING: Initial state after submission; editable and cancellable by employee.
 * - APPROVED: Application approved by an administrator.
 * - REJECTED: Application rejected by an administrator.
 * - CANCELLED: Application withdrawn by the employee before a decision; kept for history.
 */
public enum LeaveStatus {
    PENDING, APPROVED, REJECTED, CANCELLED
}
