package com.rideswift.auth_service.dto;

public record RefreshRequest(String refreshToken, String deviceInfo) {
}