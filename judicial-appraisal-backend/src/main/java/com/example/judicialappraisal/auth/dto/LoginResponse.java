package com.example.judicialappraisal.auth.dto;

public record LoginResponse(String token, CurrentUserInfo user) {
}
