package com.settribe.leave.entity;

/**
 * Roles for authorization in the Leave Management System.
 * - EMPLOYEE: Standard user permissions (apply, edit, cancel own leaves).
 * - ADMIN: Elevated permissions (view all employee requests, approve, reject).
 */
public enum Role {
    EMPLOYEE, ADMIN
}

