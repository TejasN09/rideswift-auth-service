package com.rideswift.auth_service.service;

import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.proc.BadJWTException;
import com.rideswift.auth_service.dto.AuthResponse;
import com.rideswift.auth_service.dto.LoginRequest;
import com.rideswift.auth_service.dto.RegisterRequest;
import com.rideswift.auth_service.event.UserCreatedEvent;
import com.rideswift.auth_service.model.RefreshToken;
import com.rideswift.auth_service.model.User;
import com.rideswift.auth_service.repository.RefreshTokenRepository;
import com.rideswift.auth_service.repository.UserRepository;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.rideswift.auth_service.exception.BadRequest;
import com.rideswift.auth_service.exception.UnauthorizedException;

import jakarta.transaction.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final EventPublisher eventPublisher;
    private static final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder encoder,
            JwtService jwtService,
            RedisTemplate<String, Object> redisTemplate,
            EventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.redisTemplate = redisTemplate;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) throws JOSEException {
        // 1. basic validations (password length, complexity, email format)
        if (userRepository.existsByUsername(req.username()))
            throw new BadRequest("username exists");
        if (userRepository.existsByEmail(req.email().toLowerCase()))
            throw new BadRequest("email exists");

        // 2. create user
        User user = new User();
        user.setUsername(req.username());
        user.setEmail(req.email().toLowerCase());
        user.setPassword(encoder.encode(req.password()));
        user.setRoles(req.role());
        userRepository.save(user);

        // 3. Publish event AFTER COMMIT
        // Register a callback to publish after transaction commits:
        TransactionSynchronizationManager
                .registerSynchronization((TransactionSynchronization) new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        eventPublisher.publishUserCreated(
                                new UserCreatedEvent(user.getId(), user.getUsername(), user.getEmail(),
                                        user.getRoles()));
                    }
                });

        // 4. Option: issue tokens if you auto-login
        String accessToken = jwtService.generateAccessToken(user.getId(), List.of(user.getRoles()));
        String refreshToken = createRefreshTokenString();
        persistRefreshToken(user, refreshToken, req.deviceInfo());

        return new AuthResponse(accessToken, refreshToken);
    }

    public AuthResponse login(LoginRequest req) throws JOSEException {
        // 1. Find user or throw
        User user = userRepository.findByUsername(req.username())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        // 2. Check account lock
        if (user.getLockedUntil() != null && Instant.now().isBefore(user.getLockedUntil())) {
            throw new UnauthorizedException("Account is locked until " + user.getLockedUntil());
        }

        // 3. Verify password
        if (!encoder.matches(req.password(), user.getPassword())) {
            recordFailedLoginAttempt(req.username(), req.clientIp());
            throw new UnauthorizedException("Invalid credentials");
        }

        // 4. Reset failed attempts
        resetFailedLoginAttempts(req.username(), req.clientIp());

        // 5. Create tokens
        String accessToken = jwtService.generateAccessToken(user.getId(), List.of(user.getRoles()));
        String refreshToken = createRefreshTokenString();
        persistRefreshToken(user, refreshToken, req.deviceInfo());

        return new AuthResponse(accessToken, refreshToken);
    }

    @Transactional
    public AuthResponse refresh(String tokenString, String deviceInfo) throws JOSEException {
        String[] parts = tokenString.split("\\.", 2);
        if (parts.length != 2)
            throw new UnauthorizedException("invalid refresh token format");

        UUID tokenId = UUID.fromString(parts[0]);
        String secret = parts[1];

        RefreshToken stored = refreshTokenRepository.findById(tokenId)
                .orElseThrow(() -> new UnauthorizedException("refresh token not found"));

        if (stored.isRevoked() || stored.getExpiryDate().isBefore(Instant.now())) {
            // possible replay attack prevention: revoke all tokens for this user & require
            // login
            throw new UnauthorizedException("refresh token revoked or expired");
        }

        // verify secret via BCrypt
        if (!encoder.matches(secret, stored.getTokenHash())) {
            throw new UnauthorizedException("refresh token invalid");
        }

        // rotate: revoke old token and create new one
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        String newRefreshToken = createRefreshTokenString();
        persistRefreshToken(stored.getUser(), newRefreshToken, deviceInfo);

        String newAccess = jwtService.generateAccessToken(stored.getUser().getId(),
                List.of(stored.getUser().getRoles()));
        return new AuthResponse(newAccess, newRefreshToken);
    }

    public void logout(String accessToken, String refreshTokenString)
            throws BadJWTException, ParseException, JOSEException {
        // Add access token jti to Redis blacklist until token expiry
        String jti = jwtService.extractJti(accessToken);
        long ttl = jwtService.getExpirySeconds(accessToken);
        redisTemplate.opsForValue().set("blacklist:access:" + jti, "1", Duration.ofSeconds(ttl));

        try {
            UUID tokenId = UUID.fromString(refreshTokenString.split("\\.")[0]);
            refreshTokenRepository.findById(tokenId).ifPresent(rt -> {
                rt.setRevoked(true);
                refreshTokenRepository.save(rt);
            });
        } catch (Exception ignored) {
        }
    }

    private RefreshToken persistRefreshToken(User user, String tokenString, String deviceInfo) {
        String[] parts = tokenString.split("\\.", 2);
        UUID tokenId = UUID.fromString(parts[0]);
        String secret = parts[1];

        RefreshToken rt = new RefreshToken();
        rt.setTokenId(tokenId);
        rt.setTokenHash(encoder.encode(secret)); // hashed secret
        rt.setUser(user);
        rt.setDeviceInfo(deviceInfo);
        rt.setIssuedAt(Instant.now());
        rt.setExpiryDate(Instant.now().plus(30, ChronoUnit.DAYS));
        rt.setRevoked(false);
        return refreshTokenRepository.save(rt);
    }

    private String createRefreshTokenString() {
        UUID tokenId = UUID.randomUUID();
        byte[] secretBytes = new byte[64];
        secureRandom.nextBytes(secretBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        return tokenId.toString() + "." + secret;
    }

    private void recordFailedLoginAttempt(String username, String ip) {
        String key = "login:fail:" + username;
        Long fails = redisTemplate.opsForValue().increment(key, 1);
        redisTemplate.expire(key, Duration.ofMinutes(15));
        if (fails != null && fails >= 5) {
            // lock user account in DB for 15 minutes
            userRepository.lockAccount(username, Instant.now().plus(15, ChronoUnit.MINUTES));
        }
    }

    private void resetFailedLoginAttempts(String username, String ip) {
        redisTemplate.delete("login:fail:" + username);
    }
}
