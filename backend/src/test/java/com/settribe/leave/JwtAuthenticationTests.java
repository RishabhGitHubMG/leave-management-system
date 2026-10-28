package com.settribe.leave;

import com.jayway.jsonpath.JsonPath;
import com.settribe.leave.entity.Role;
import com.settribe.leave.entity.User;
import com.settribe.leave.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.jwt.secret=" + JwtAuthenticationTests.SECRET)
@AutoConfigureMockMvc
class JwtAuthenticationTests {

    static final String SECRET = "test-secret-that-is-at-least-32-characters-long";
    private static final String OTHER_SECRET = "a-different-secret-that-is-also-32-bytes";
    private static final String PASSWORD = "jwt-test-pass-123";
    private static final String AUTH = "Authorization";
    private static final String ME = "/api/users/me";
    private static final String ADMIN_USERS = "/api/admin/users";

    @Value("${app.jwt.issuer}")
    private String issuer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void loginReturnsVerifiableHs256TokenWithExpectedClaims() throws Exception {
        String email = createUser(Role.EMPLOYEE);
        long userId = userIdOf(email);

        String token = loginToken(email);
        Jws<Claims> jws = parseWithTestKey(token);

        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(jws.getPayload().getSubject()).isEqualTo(Long.toString(userId));
        assertThat(jws.getPayload().getIssuer()).isEqualTo(issuer);
        assertThat(jws.getPayload().getId()).isNotBlank();
        assertThat(jws.getPayload().get("role", String.class)).isEqualTo("EMPLOYEE");
        assertThat(jws.getPayload().getExpiration().getTime() - jws.getPayload().getIssuedAt().getTime())
                .isEqualTo(Duration.ofHours(24).toMillis());
        assertThat(payloadJson(token)).doesNotContain(email).doesNotContain("password");
    }

    @Test
    void registrationReturnsUsableToken() throws Exception {
        String email = "jwt-register-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Jwt Register\",\"email\":\"" + email
                                + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");

        parseWithTestKey(token);
        mockMvc.perform(get(ME).header(AUTH, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void failedLoginsNeverReceiveToken() throws Exception {
        String email = createUser(Role.EMPLOYEE);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "wrong-password-999")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.token").doesNotExist());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("nobody-" + UUID.randomUUID() + "@example.com", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String email = createUser(Role.EMPLOYEE);
        Date past = new Date(System.currentTimeMillis() - 120_000L);
        String expired = signed(testKey(), Long.toString(userIdOf(email)), past, new Date(past.getTime() + 60_000L));

        mockMvc.perform(get(ME).header(AUTH, bearer(expired))).andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedPayloadIsRejected() throws Exception {
        String token = loginToken(createUser(Role.EMPLOYEE));
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(payload).contains("\"role\":\"EMPLOYEE\"");
        String escalated = payload.replace("\"role\":\"EMPLOYEE\"", "\"role\":\"ADMIN\"");
        String forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(escalated.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        mockMvc.perform(get(ADMIN_USERS).header(AUTH, bearer(forged))).andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedSignatureIsRejected() throws Exception {
        String token = loginToken(createUser(Role.EMPLOYEE));
        int middle = token.lastIndexOf('.') + 20;
        String tampered = token.substring(0, middle)
                + (token.charAt(middle) == 'A' ? 'B' : 'A')
                + token.substring(middle + 1);

        mockMvc.perform(get(ME).header(AUTH, bearer(tampered))).andExpect(status().isUnauthorized());
    }

    @Test
    void unsignedAlgNoneTokenIsRejected() throws Exception {
        String token = loginToken(createUser(Role.ADMIN));
        String[] parts = token.split("\\.");
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get(ADMIN_USERS).header(AUTH, bearer(header + "." + parts[1] + ".")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() throws Exception {
        String email = createUser(Role.ADMIN);
        SecretKey foreign = Keys.hmacShaKeyFor(OTHER_SECRET.getBytes(StandardCharsets.UTF_8));
        String forged = signed(foreign, Long.toString(userIdOf(email)), new Date(), null);

        mockMvc.perform(get(ADMIN_USERS).header(AUTH, bearer(forged))).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithWrongIssuerIsRejected() throws Exception {
        String email = createUser(Role.ADMIN);
        String forged = Jwts.builder()
                .subject(Long.toString(userIdOf(email)))
                .issuer("some-other-issuer")
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(testKey(), Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get(ADMIN_USERS).header(AUTH, bearer(forged))).andExpect(status().isUnauthorized());
    }

    @Test
    void legacyEmailSubjectTokenIsRejected() throws Exception {
        String email = createUser(Role.ADMIN);
        String legacy = signed(testKey(), email, new Date(), null);

        mockMvc.perform(get(ADMIN_USERS).header(AUTH, bearer(legacy))).andExpect(status().isUnauthorized());
    }

    @Test
    void missingAndMalformedTokensAreRejected() throws Exception {
        mockMvc.perform(get(ME)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ME).header(AUTH, "Bearer ")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ME).header(AUTH, "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ME).header(AUTH, "Basic dXNlcjpwYXNz")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/leaves").header(AUTH, "Bearer a.b.c")).andExpect(status().isUnauthorized());
    }

    @Test
    void lockedAccountCannotLoginAndExistingTokenIsRejected() throws Exception {
        String email = createUser(Role.EMPLOYEE);
        String token = loginToken(email);
        setLockedUntil(email, LocalDateTime.now().plusMinutes(15));

        mockMvc.perform(get(ME).header(AUTH, bearer(token))).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, PASSWORD)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void inactiveAccountCannotLoginAndExistingTokenIsRejected() throws Exception {
        String email = createUser(Role.EMPLOYEE);
        String token = loginToken(email);
        setActive(email, false);

        mockMvc.perform(get(ME).header(AUTH, bearer(token))).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void roleChangesInDatabaseApplyToExistingToken() throws Exception {
        String email = createUser(Role.EMPLOYEE);
        String token = loginToken(email);
        mockMvc.perform(get(ADMIN_USERS).header(AUTH, bearer(token))).andExpect(status().isForbidden());

        setRole(email, Role.ADMIN);

        mockMvc.perform(get(ADMIN_USERS).header(AUTH, bearer(token))).andExpect(status().isOk());
    }

    @Test
    void roleBasedAccessControlIsEnforced() throws Exception {
        String employee = loginToken(createUser(Role.EMPLOYEE));
        String manager = loginToken(createUser(Role.MANAGER));
        String admin = loginToken(createUser(Role.ADMIN));

        mockMvc.perform(get(ADMIN_USERS)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ADMIN_USERS).header(AUTH, bearer(employee))).andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN_USERS).header(AUTH, bearer(manager))).andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN_USERS).header(AUTH, bearer(admin))).andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/leaves").header(AUTH, bearer(employee))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/leaves").header(AUTH, bearer(manager))).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/leaves").header(AUTH, bearer(admin))).andExpect(status().isOk());
    }

    private String createUser(Role role) {
        String email = "jwt-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        userRepo.save(User.builder()
                .name("Jwt " + role.name().toLowerCase())
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .role(role)
                .build());
        return email;
    }

    private String loginToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private long userIdOf(String email) {
        return userRepo.findByEmail(email).orElseThrow().getId();
    }

    private void setLockedUntil(String email, LocalDateTime until) {
        User user = userRepo.findByEmail(email).orElseThrow();
        user.setLockedUntil(until);
        userRepo.save(user);
    }

    private void setActive(String email, boolean active) {
        User user = userRepo.findByEmail(email).orElseThrow();
        user.setActive(active);
        userRepo.save(user);
    }

    private void setRole(String email, Role role) {
        User user = userRepo.findByEmail(email).orElseThrow();
        user.setRole(role);
        userRepo.save(user);
    }

    private Jws<Claims> parseWithTestKey(String token) {
        return Jwts.parser().verifyWith(testKey()).requireIssuer(issuer).build().parseSignedClaims(token);
    }

    private String payloadJson(String token) {
        return new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
    }

    private String signed(SecretKey key, String subject, Date issuedAt, Date expiresAt) {
        var builder = Jwts.builder()
                .subject(subject)
                .issuer(issuer)
                .issuedAt(issuedAt)
                .claim("role", "ADMIN");
        if (expiresAt != null) builder.expiration(expiresAt);
        return builder.signWith(key, Jwts.SIG.HS256).compact();
    }

    private static SecretKey testKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String loginJson(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }
}
