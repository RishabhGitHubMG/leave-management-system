package com.settribe.leave.entity;

/**
 * Roles for authorization in the Leave Management System.
 * - EMPLOYEE: apply, edit and cancel own pending leave; view own history.
 * - MANAGER: everything an employee can do, plus review leave of direct reports.
 * - HR: review leave of all employees (leave types and policies come in a later phase).
 * - ADMIN: manage users and roles; review leave of all employees.
 */
public enum Role {
    EMPLOYEE, MANAGER, HR, ADMIN
}
