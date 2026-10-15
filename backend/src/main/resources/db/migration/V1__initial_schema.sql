-- MySQL 8.0.16+ schema. The target database must be provisioned with utf8mb4 / utf8mb4_unicode_ci,
-- matching sql/schema.sql. This migration intentionally does not create or select a database.

CREATE TABLE users (
  id                    BIGINT       NOT NULL AUTO_INCREMENT,
  name                  VARCHAR(100) NOT NULL,
  email                 VARCHAR(150) NOT NULL,
  password              VARCHAR(255) NOT NULL,
  role                  VARCHAR(20)  NOT NULL DEFAULT 'EMPLOYEE',
  department            VARCHAR(100) NULL,
  designation           VARCHAR(100) NULL,
  phone_number          VARCHAR(25)  NULL,
  employee_id           VARCHAR(50)  NULL,
  manager_id            BIGINT       NULL,
  joining_date          DATE         NULL,
  employment_type       VARCHAR(20)  NULL,
  active                BOOLEAN      NOT NULL DEFAULT TRUE,
  failed_login_attempts INT          NOT NULL DEFAULT 0,
  locked_until          DATETIME(6)  NULL,
  created_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  CONSTRAINT uq_users_email UNIQUE (email),
  CONSTRAINT fk_users_manager FOREIGN KEY (manager_id)
    REFERENCES users (id),
  CONSTRAINT chk_users_role CHECK (role IN ('EMPLOYEE', 'MANAGER', 'HR', 'ADMIN')),
  CONSTRAINT chk_users_employment_type CHECK (
    employment_type IS NULL OR employment_type IN ('FULL_TIME', 'INTERN', 'CONTRACT')),
  INDEX idx_users_manager (manager_id)
) ENGINE=InnoDB;

CREATE TABLE leave_requests (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  user_id       BIGINT       NOT NULL,
  leave_type    VARCHAR(20)  NOT NULL,
  from_date     DATE         NOT NULL,
  to_date       DATE         NOT NULL,
  reason        VARCHAR(500) NOT NULL,
  status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
  admin_remarks VARCHAR(500) NULL,
  created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  CONSTRAINT fk_leave_user FOREIGN KEY (user_id)
    REFERENCES users (id),
  CONSTRAINT chk_leave_type CHECK (leave_type IN ('SICK', 'CASUAL', 'ANNUAL', 'UNPAID')),
  CONSTRAINT chk_leave_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
  CONSTRAINT chk_leave_dates CHECK (to_date >= from_date),
  INDEX idx_leave_user (user_id),
  INDEX idx_leave_status (status)
) ENGINE=InnoDB;

CREATE TABLE notifications (
  id         BIGINT        NOT NULL AUTO_INCREMENT,
  user_id    BIGINT        NOT NULL,
  message    VARCHAR(500)  NOT NULL,
  is_read    BOOLEAN       NOT NULL DEFAULT FALSE,
  created_at DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  CONSTRAINT fk_notification_user FOREIGN KEY (user_id)
    REFERENCES users (id),
  INDEX idx_notification_user_read (user_id, is_read)
) ENGINE=InnoDB;
