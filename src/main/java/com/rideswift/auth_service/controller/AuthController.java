package com.rideswift.auth_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jose.JOSEException;
import com.rideswift.auth_service.dto.AuthResponse;
import com.rideswift.auth_service.dto.LoginRequest;
import com.rideswift.auth_service.dto.RefreshRequest;
import com.rideswift.auth_service.dto.RegisterRequest;
import com.rideswift.auth_service.service.AuthService;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/test")
    public ResponseEntity<String> testApi() {
        return ResponseEntity.ok("Auth Service is working");
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            AuthResponse response = authService.register(request);
            return ResponseEntity.ok(response);
        } catch (JOSEException e) {
            return ResponseEntity.status(500).body("Token generation error");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (JOSEException e) {
            return ResponseEntity.status(500).body("Token generation error");
        } catch (RuntimeException e) {
            if (e.getMessage().equals("User not found")) {
                return ResponseEntity.status(404).body("User not found");
            } else if (e.getMessage().equals("Invalid credentials")) {
                return ResponseEntity.status(401).body("Invalid credentials");
            } else {
                return ResponseEntity.badRequest().body(e.getMessage());
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest request) {
        try {
            AuthResponse response = authService.refresh(request.refreshToken(), request.deviceInfo());
            return ResponseEntity.ok(response);
        } catch (JOSEException e) {
            return ResponseEntity.status(500).body("Token generation error");
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String accessToken,
            @RequestBody String refreshToken) {
        try {
            authService.logout(accessToken.replace("Bearer ", ""), refreshToken);
            return ResponseEntity.ok("Logged out successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Internal server error");
        }
    }
}
