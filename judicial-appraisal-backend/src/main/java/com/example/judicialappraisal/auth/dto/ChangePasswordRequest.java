package com.example.judicialappraisal.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 6, max = 64) String newPassword,
        @NotBlank @Size(min = 6, max = 64) String confirmPassword
) {
}
