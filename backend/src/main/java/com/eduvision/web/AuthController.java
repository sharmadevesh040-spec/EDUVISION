package com.eduvision.web;

import com.eduvision.domain.Role;
import com.eduvision.domain.User;
import com.eduvision.repository.UserRepository;
import com.eduvision.security.CurrentUser;
import com.eduvision.security.JwtService;
import com.eduvision.util.PasswordUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** Authentication endpoints. Passwords are PBKDF2-hashed before they are ever persisted. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final JwtService jwt;
    private final CurrentUser currentUser;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository users, JwtService jwt, CurrentUser currentUser,
                          PasswordEncoder passwordEncoder) {
        this.users = users;
        this.jwt = jwt;
        this.currentUser = currentUser;
        this.passwordEncoder = passwordEncoder;
    }

    public record RegisterRequest(@NotBlank String name,
                                  @NotBlank @Email String email,
                                  @NotBlank @Size(min = 8, message = "must be at least 8 characters") String password,
                                  @NotNull Role role,
                                  String grade) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record UserDto(Long id, String name, String email, Role role, String grade) {
        static UserDto of(User u) {
            return new UserDto(u.id, u.name, u.email, u.role, u.grade);
        }
    }

    public record AuthResponse(String token, long expiresInSeconds, UserDto user) {
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
        if (req.role() == Role.TEACHER && (req.grade() == null || req.grade().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Teachers must provide a grade/board");
        }
        User user = new User(req.name().trim(), req.email().trim().toLowerCase(),
                PasswordUtil.hash(req.password()), req.role(),
                req.grade() == null ? null : req.grade().trim());
        users.save(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(jwt.generateToken(user), jwt.ttlSeconds(), UserDto.of(user)));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        // Support both hashing schemes: PBKDF2 (domain) and BCrypt (Spring Security managed).
        boolean ok = user.passwordHash.startsWith(PasswordUtil.PREFIX)
                ? PasswordUtil.verify(req.password(), user.passwordHash)
                : passwordEncoder.matches(req.password(), user.passwordHash);
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return new AuthResponse(jwt.generateToken(user), jwt.ttlSeconds(), UserDto.of(user));
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        User user = currentUser.get();
        return Map.of(
                "user", UserDto.of(user),
                "securityNote", "Passwords are stored as PBKDF2-HMAC-SHA256 hashes and never in plain text.");
    }
}