package com.dts.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateUserRequest(
        @Size(min = 3, max = 20, message = "Username must be 3-20 characters")
        String username,

        @Email(message = "Email must be valid")
        String email,

        @Size(max = 100, message = "Full name max 100 characters")
        String fullName,

        LocalDate birthOfDate,

        @Size(max = 20, message = "Phone number max 20 characters")
        String phoneNumber,

        String status
) {}
