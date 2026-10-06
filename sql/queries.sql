-- Employee Leave Management System: SQL assessment queries
-- Run with:  mysql -u leaveapp -p < sql/queries.sql
-- Ids/emails below are examples: change them to match your data.

USE leave_management;

-- 1. Display all users (password hash deliberately not selected)
SELECT id, name, email, role, created_at
FROM users;

-- 2. Display leave requests for a user (by email)
SELECT lr.id, lr.leave_type, lr.from_date, lr.to_date, lr.reason, lr.status, lr.created_at
FROM leave_requests lr
WHERE lr.user_id = (SELECT id FROM users WHERE email = 'amit@test.com')
ORDER BY lr.created_at DESC;

-- 3. Count requests by status
SELECT status, COUNT(*) AS total
FROM leave_requests
GROUP BY status;

-- 4. Find pending requests
SELECT id, user_id, leave_type, from_date, to_date, reason, created_at
FROM leave_requests
WHERE status = 'PENDING';

-- 5. Find approved requests ordered by date (latest leave first)
SELECT id, user_id, leave_type, from_date, to_date, status
FROM leave_requests
WHERE status = 'APPROVED'
ORDER BY from_date DESC;

-- 6. Join users with leave requests
SELECT u.id AS user_id, u.name, u.email,
       lr.id AS request_id, lr.leave_type, lr.from_date, lr.to_date, lr.status
FROM users u
JOIN leave_requests lr ON lr.user_id = u.id
ORDER BY u.name, lr.from_date;

-- 7. Update request status (only while still pending, same rule as the API)
UPDATE leave_requests
SET status = 'APPROVED'
WHERE id = 1
  AND status = 'PENDING';

-- 8. Delete a pending request (same rule as the API: approved/rejected stay)
DELETE FROM leave_requests
WHERE id = 1
  AND status = 'PENDING';

-- Bonus: request count per employee, including employees with none
SELECT u.name, COUNT(lr.id) AS total_requests
FROM users u
LEFT JOIN leave_requests lr ON lr.user_id = u.id
GROUP BY u.id, u.name
ORDER BY total_requests DESC;
