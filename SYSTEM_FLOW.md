# Employee Leave Management System — Architecture & Workflow Guide

This document provides a comprehensive technical guide explaining how the Employee Leave Management System works from end to end: its architecture, data flows, security mechanisms, state management, and file breakdown.

---

## 1. High-Level Architecture Overview

The system follows a modern decoupled client-server architecture:

```
[ Client Browser ]
        │
        │ HTTP / HTTPS (JSON)
        ▼
[ React 18 Single Page App (Vite) ]
  ├── AuthContext (Global Session & LocalStorage Sync)
  ├── Axios API Client (Base URL detection & JWT interceptor)
  └── UI Components (AuthPage, Dashboard, LeaveForm)
        │
        │ REST API calls with "Authorization: Bearer <JWT>"
        ▼
[ Spring Boot 4 Backend (Java 17) ]
  ├── Spring Security Filter Chain
  │     ├── CorsFilter
  │     └── JwtAuthFilter (Extracts and validates token per request)
  ├── Controllers (REST Endpoints: /api/auth, /api/leaves, /api/admin/leaves)
  ├── Services (Business logic, validation, transaction boundaries)
  ├── Repositories (Spring Data JPA interfaces)
  └── Database (MySQL 8 / H2 in-memory compatibility fallback)
```

---

## 2. Core Entities & Roles

### User Roles
* **`EMPLOYEE`**: Default role assigned upon registration. Can create, view, update, and cancel their own leave requests while in `PENDING` status.
* **`ADMIN`**: Privileged role. Can view all organizational leave requests across all employees, filter them, and approve or reject pending requests. Also has access to apply for personal leave.

### Leave Types
* `SICK`: Medical or health-related absence.
* `CASUAL`: Short personal time off.
* `ANNUAL`: Scheduled vacation / annual holiday.
* `UNPAID`: Leave without pay.

### Leave Request Statuses
* `PENDING`: Initial state upon submission. Only pending requests can be modified, cancelled, approved, or rejected.
* `APPROVED`: Finalized positive decision made by an Administrator. Immutable.
* `REJECTED`: Finalized negative decision made by an Administrator. Immutable.

---

## 3. End-to-End System Workflows

### Flow 1: User Registration
```mermaid
sequenceDiagram
    autonumber
    actor User as Employee
    participant UI as React UI (AuthPage)
    participant AuthContext as AuthContext
    participant API as AuthController (/api/auth/register)
    participant Service as AuthService
    participant Repo as UserRepository
    participant DB as Database

    User->>UI: Fills Name, Email, Password -> Clicks "Register"
    UI->>UI: Validates format & password length (min 8 chars)
    UI->>API: POST /api/auth/register {name, email, password}
    API->>Service: register(request)
    Service->>Repo: existsByEmail(email)
    alt Email already taken
        Repo-->>Service: true
        Service-->>API: throws ApiException (409 Conflict)
        API-->>UI: 409 ApiError "Email is already registered"
        UI-->>User: Displays error banner
    else Email available
        Service->>Service: passwordEncoder.encode(password) (BCrypt)
        Service->>Repo: save(User with role EMPLOYEE)
        Repo->>DB: INSERT INTO users ...
        Service->>Service: jwtUtil.generateToken(user)
        Service-->>API: AuthResponse {token, id, name, email, role}
        API-->>UI: 200 OK + AuthResponse
        UI->>AuthContext: login(data)
        AuthContext->>AuthContext: Save user + token in localStorage
        UI->>UI: Redirect to /dashboard
    end
```

---

### Flow 2: User Login & JWT Authentication
```mermaid
sequenceDiagram
    autonumber
    actor User as User (Employee or Admin)
    participant UI as React UI (AuthPage)
    participant API as AuthController (/api/auth/login)
    participant Sec as Spring Security (AuthenticationManager)
    participant JWT as JwtUtil
    participant Context as React AuthContext

    User->>UI: Enters Email & Password -> Clicks "Sign in"
    UI->>API: POST /api/auth/login {email, password}
    API->>Sec: authenticate(UsernamePasswordAuthenticationToken)
    alt Invalid Credentials
        Sec-->>API: BadCredentialsException
        API-->>UI: 401 ApiError "Invalid email or password"
        UI-->>User: Displays error message
    else Valid Credentials
        Sec-->>API: Authentication success
        API->>JWT: generateToken(userDetails)
        JWT-->>API: Signed JWT token (HS256)
        API-->>UI: 200 OK {token, user: {id, name, email, role}}
        UI->>Context: login({token, user})
        Context->>Context: Persist in localStorage ("token", "user")
        UI->>UI: Navigate to /dashboard
    end
```

---

### Flow 3: Optional Admin Account Seeding
1. At application boot, `AdminInitializer` implements Spring's `CommandLineRunner`.
2. If `ADMIN_EMAIL` and `ADMIN_PASSWORD` are both set, it checks whether that email exists.
3. If not, it creates an `ADMIN` user named `System Admin` with a BCrypt-hashed password. `ADMIN_PASSWORD` must be at least 8 characters.
4. If nothing is configured or the account exists, it does nothing.

No credentials are stored in source code.

---

### Flow 4: Employee Leave Application
```mermaid
sequenceDiagram
    autonumber
    actor Emp as Employee
    participant UI as Dashboard / LeaveForm
    participant Axios as api.js (Axios Client)
    participant Filter as JwtAuthFilter
    participant Controller as LeaveController
    participant Service as LeaveService
    participant Repo as LeaveRequestRepository
    participant DB as Database

    Emp->>UI: Clicks "Apply for leave"
    Emp->>UI: Selects type, fromDate, toDate, reason
    UI->>UI: Checks fromDate <= toDate & non-empty
    UI->>Axios: api.post("/leaves", payload)
    Note over Axios: Axios Request Interceptor adds:<br/>Authorization: Bearer <token>
    Axios->>Filter: HTTP POST /api/leaves
    Filter->>Filter: Validates token signature & expiration
    Filter->>Filter: Sets SecurityContextHolder authentication
    Filter->>Controller: Passes to LeaveController.apply(...)
    Controller->>Service: apply(currentEmail, dto)
    Service->>Service: Validates date logic (toDate >= fromDate)
    Service->>Repo: save(LeaveRequest with status PENDING)
    Repo->>DB: INSERT INTO leave_requests ...
    Service-->>Controller: LeaveResponse DTO
    Controller-->>Axios: 201 Created + LeaveResponse
    Axios-->>UI: Success
    UI->>UI: Closes form, shows "Request submitted", refreshes list
```

---

### Flow 5: Modifying or Cancelling Pending Leaves
* **Editing a Request**:
  1. The employee clicks **Edit** on a `PENDING` request item.
  2. The `LeaveForm` opens populated with the existing request details.
  3. The employee modifies dates, reason, or type and clicks **Save changes**.
  4. Calls `PUT /api/leaves/{id}`.
  5. `LeaveService` confirms:
     - The request exists.
     - The request belongs to the authenticated user (returns `404 Not Found` if accessed by another user to prevent ID enumeration).
     - The status is currently `PENDING` (returns `409 Conflict` if already processed).
  6. The record is updated and saved.
* **Cancelling a Request**:
  1. The employee clicks **Cancel** and confirms via prompt.
  2. Calls `DELETE /api/leaves/{id}`.
  3. The backend confirms ownership and `PENDING` status, then deletes the record from the database.

---

### Flow 6: Admin Review Flow (Approve / Reject)
```mermaid
sequenceDiagram
    autonumber
    actor Admin as Administrator
    participant UI as Dashboard ("All requests" tab)
    participant API as AdminLeaveController
    participant Service as LeaveService
    participant Repo as LeaveRequestRepository
    participant DB as Database

    Admin->>UI: Selects "All requests" tab
    UI->>API: GET /api/admin/leaves?status=PENDING
    Note over API: Verified hasRole('ADMIN') by Spring Security
    API->>Service: findAll(status, type)
    Service->>Repo: Queries all leave records with User join
    Repo-->>Service: List<LeaveRequest>
    Service-->>API: List<LeaveResponse>
    API-->>UI: 200 OK with items
    Admin->>UI: Clicks "Approve" (or "Reject") on an item
    UI->>API: PUT /api/admin/leaves/{id}/status {status: "APPROVED"}
    API->>Service: updateStatus(id, APPROVED)
    Service->>Repo: findById(id)
    Service->>Service: Verifies current status == PENDING
    Service->>Service: Sets status = APPROVED
    Service->>Repo: save(request)
    Repo->>DB: UPDATE leave_requests SET status = 'APPROVED' ...
    Service-->>API: Updated LeaveResponse
    API-->>UI: 200 OK
    UI->>UI: Refreshes list with updated status badge
```

---

## 4. Security & Error Handling Architecture

### 1. Stateless JWT Security
* Every API request outside `/api/auth/**` must carry an `Authorization: Bearer <token>` header.
* `JwtAuthFilter` intercepts every incoming HTTP request:
  1. Extracts the Bearer token.
  2. Validates signature with HMAC-SHA256 (`jwt.secret`).
  3. Verifies token expiration.
  4. Loads user authorities into Spring Security's `SecurityContextHolder`.

### 2. Role-Based Access Control (RBAC)
* `/api/auth/**` -> Public.
* `/api/leaves/**` -> Accessible by authenticated users with role `EMPLOYEE` or `ADMIN`.
* `/api/admin/**` -> Strictly restricted to users with role `ADMIN`. Requests from standard employees receive `403 Forbidden`.

### 3. ID Probing Protection
* When an employee attempts to view, edit, or cancel a leave ID belonging to another employee, the system intentionally returns **`404 Not Found`** rather than `403 Forbidden`.
* This prevents attackers from enumerating valid leave request IDs across the organization.

### 4. Global Exception Handler (`GlobalExceptionHandler`)
All application exceptions are intercepted and converted into standardized JSON responses (`ApiError`):
* `MethodArgumentNotValidException` -> Returns `400 Bad Request` with field validation errors.
* `ApiException` -> Returns custom HTTP status codes (e.g. `409 Conflict` for duplicate emails or invalid state transitions).
* `BadCredentialsException` -> Returns `401 Unauthorized`.
* `AccessDeniedException` -> Returns `403 Forbidden`.

---

## 5. Codebase Structure & File Mapping

### Backend (`/backend`)
```
backend/src/main/java/com/settribe/leave/
├── LeaveApplication.java           # Spring Boot bootstrap & main()
├── config/
│   ├── SecurityConfig.java         # FilterChain, CORS, BCrypt, Security routes
│   └── AdminInitializer.java      # Seeds default admin account on startup
├── controller/
│   ├── AuthController.java         # /api/auth (register, login)
│   ├── LeaveController.java        # /api/leaves (employee actions)
│   └── AdminLeaveController.java   # /api/admin/leaves (admin actions)
├── service/
│   ├── AuthService.java            # User registration & login business logic
│   └── LeaveService.java           # Leave business logic, validations, CRUD
├── repository/
│   ├── UserRepository.java         # User entity JPA repository
│   └── LeaveRequestRepository.java # LeaveRequest JPA repository + query filters
├── security/
│   ├── JwtAuthFilter.java          # Per-request JWT authentication filter
│   ├── JwtUtil.java                # JWT token creation, signing & parsing
│   └── UserDetailsServiceImpl.java # Spring Security user details provider
├── entity/
│   ├── User.java                   # User database entity
│   ├── LeaveRequest.java           # Leave request database entity
│   ├── Role.java                   # Enum: EMPLOYEE, ADMIN
│   ├── LeaveStatus.java            # Enum: PENDING, APPROVED, REJECTED
│   └── LeaveType.java              # Enum: SICK, CASUAL, ANNUAL, UNPAID
├── dto/
│   ├── AuthResponse.java           # Authentication response with JWT
│   ├── LoginRequest.java           # Login request credentials
│   ├── RegisterRequest.java        # Registration request payload
│   ├── LeaveRequestDto.java        # Leave creation/update payload
│   ├── LeaveResponse.java          # Formatted leave request response
│   └── StatusUpdateRequest.java    # Status change payload (Admin)
└── exception/
    ├── GlobalExceptionHandler.java # @ControllerAdvice centralized exception mapper
    ├── ApiException.java           # Custom runtime exception with HTTP status
    └── ApiError.java               # Standardized error payload
```

### Frontend (`/frontend`)
```
frontend/src/
├── main.jsx                        # React root entrypoint
├── App.jsx                         # React Router routes and Protected wrappers
├── auth.jsx                        # AuthContext, user state, localStorage sync
├── api.js                          # Axios instance, baseURL resolution, 15s timeout, JWT interceptor
├── styles.css                      # Design tokens, responsive grid, dark/light theme, badges
├── pages/
│   ├── LandingPage.jsx             # Public landing page with hero, mockup preview, benefits, security
│   ├── AuthPage.jsx                # Dual-mode Sign In / Sign Up component with inline validation & strength meter
│   └── Dashboard.jsx               # Main application dashboard (4 KPIs, search, filters, sorting, modals)
└── components/
    ├── SettribeLogo.jsx            # Official Settribe vector SVG brand logo with accent bar
    ├── AvatarDropdown.jsx          # Initials button with menu (dark mode toggle, logout)
    ├── LeaveBalanceWidget.jsx      # Leave allowance per type, consumed from the user's requests
    ├── LeaveForm.jsx               # Accessible modal form with duration calculation & character counter
    └── ConfirmModal.jsx            # Accessible in-app confirmation modal (replaces window.confirm)
```


---

## 6. Live Deployment Environment

* **Live Frontend**: [https://leave-management-system-frontend-8v9l.onrender.com](https://leave-management-system-frontend-8v9l.onrender.com)
* **Live Backend**: [https://leave-management-system-1-9uph.onrender.com](https://leave-management-system-1-9uph.onrender.com)
* **Admin account**: set `ADMIN_EMAIL` and `ADMIN_PASSWORD` on the backend to seed one.
