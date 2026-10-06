CREATE DATABASE IF NOT EXISTS leave_management
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE leave_management;

CREATE TABLE IF NOT EXISTS users (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  name        VARCHAR(100) NOT NULL,
  email       VARCHAR(150) NOT NULL,
  password    VARCHAR(255) NOT NULL,
  role        VARCHAR(20)  NOT NULL DEFAULT 'EMPLOYEE',
  created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uq_users_email UNIQUE (email),
  CONSTRAINT chk_users_role CHECK (role IN ('EMPLOYEE', 'ADMIN'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS leave_requests (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  user_id     BIGINT       NOT NULL,
  leave_type  VARCHAR(20)  NOT NULL,
  from_date   DATE         NOT NULL,
  to_date     DATE         NOT NULL,
  reason      VARCHAR(500) NOT NULL,
  status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
  created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_leave_user FOREIGN KEY (user_id)
    REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT chk_leave_type CHECK (leave_type IN ('SICK', 'CASUAL', 'ANNUAL', 'UNPAID')),
  CONSTRAINT chk_leave_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
  CONSTRAINT chk_leave_dates CHECK (to_date >= from_date),
  INDEX idx_leave_user (user_id),
  INDEX idx_leave_status (status)
) ENGINE=InnoDB;
