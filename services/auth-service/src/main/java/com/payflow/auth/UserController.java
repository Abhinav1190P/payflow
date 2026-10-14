package com.payflow.auth;

import com.payflow.auth.dto.UserResponse;
import com.payflow.common.api.ApiResponse;
import com.payflow.common.error.NotFoundException;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Used by payment-service (through OpenFeign) to turn a transfer's recipient email into a user id.
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository users;

    public UserController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/lookup")
    public ApiResponse<UserResponse> lookup(@RequestParam @NotBlank @Email String email) {
        return ApiResponse.of(users.findByEmailIgnoreCase(email.trim())
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("User")));
    }
}
