package com.dts.identity.dto.response;

import com.dts.identity.entity.User;
import com.dts.identity.entity.User.UserStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        String fullName,
        LocalDate birthOfDate,
        String phoneNumber,
        UserStatus status,
        Instant emailVerifiedAt,
        Instant phoneVerifiedAt,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getBirthOfDate(),
                user.getPhoneNumber(),
                user.getStatus(),
                user.getEmailVerifiedAt(),
                user.getPhoneVerifiedAt(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
