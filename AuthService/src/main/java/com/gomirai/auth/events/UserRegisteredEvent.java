package com.gomirai.auth.events;

import java.util.UUID;

public record UserRegisteredEvent(UUID userId, String phoneNumber, String role) {}



