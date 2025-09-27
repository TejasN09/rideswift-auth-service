package com.rideswift.auth_service.dto;

public record RegisterRequest(String username, String email, String password, String role, String deviceInfo) {
}
