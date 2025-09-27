package com.rideswift.auth_service.event;

import java.util.UUID;

public record UserCreatedEvent(UUID userId, String username, String email, String roles) {}
