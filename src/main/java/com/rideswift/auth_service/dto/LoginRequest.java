package com.rideswift.auth_service.dto;

public record LoginRequest(String username, String password, String deviceInfo, String clientIp) {
}
