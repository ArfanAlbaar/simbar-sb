package com.kelompokempat.simbar.controller;

import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import com.kelompokempat.simbar.dto.ApiResponse;
import com.kelompokempat.simbar.dto.UserDTO;
import com.kelompokempat.simbar.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
@CrossOrigin(origins = "*", maxAge = 3600)
@RateLimiter(name = "controllerWideLimiter")
public class AdminController {

    private final UserService userService;

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserDTO>>> getAllUsers() {
        List<UserDTO> users = userService.findAllUsers();
        return ResponseEntity.ok(new ApiResponse<>(true, "Users retrieved", users));
    }

    @GetMapping("/users/{userId}") // Add endpoint to get user by ID
    public ResponseEntity<ApiResponse<UserDTO>> getUserByUserId(@PathVariable Long userId) {
        try {
            UserDTO user = userService.findUserById(userId);
            return ResponseEntity.ok(new ApiResponse<>(true, "User retrieved", user));
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    @PostMapping("/users/{userId}/assign-role/{roleName}")
    public ResponseEntity<ApiResponse<String>> assignRoleToUser(
            @PathVariable Long userId,
            @PathVariable String roleName) {
        try {
            userService.assignRole(userId, roleName);
            return ResponseEntity.ok(new ApiResponse<>(true, "Role assigned successfully", null));
        } catch (UsernameNotFoundException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    @PostMapping("/users/{userId}/remove-role/{roleName}")  // Add remove role endpoint
    public ResponseEntity<ApiResponse<String>> removeRoleFromUser(
            @PathVariable Long userId,
            @PathVariable String roleName) {
        try {
            userService.removeRole(userId, roleName);
            return ResponseEntity.ok(new ApiResponse<>(true, "Role removed successfully", null));
        } catch (UsernameNotFoundException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    // Add endpoints for create, update, delete, lock, and unlock user
    @PostMapping("/users")
    public ResponseEntity<ApiResponse<UserDTO>> createUser(@RequestBody UserDTO userDTO) {
        UserDTO createdUser = UserDTO.fromEntity(userService.createUser(userDTO));
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(true, "User created successfully", createdUser));
    }

    @PutMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<UserDTO>> updateUser(@PathVariable Long userId, @RequestBody UserDTO userDTO) {
        try {
            UserDTO updatedUser = UserDTO.fromEntity(userService.updateUser(userId, userDTO));
            return ResponseEntity.ok(new ApiResponse<>(true, "User updated successfully", updatedUser));
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.ok(new ApiResponse<>(true, "User deleted successfully", null));
    }

    @PostMapping("/users/{userId}/lock")
    public ResponseEntity<ApiResponse<String>> lockUserAccount(@PathVariable Long userId) {
        try {
            userService.lockUserAccount(userId);
            return ResponseEntity.ok(new ApiResponse<>(true, "User account locked successfully", null));
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    @PostMapping("/users/{userId}/unlock")
    public ResponseEntity<ApiResponse<String>> unlockUserAccount(@PathVariable Long userId) {
        try {
            userService.unlockUserAccount(userId);
            return ResponseEntity.ok(new ApiResponse<>(true, "User account unlocked successfully", null));
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    // Method ini akan menangani semua RequestNotPermitted exception
    // yang terjadi di dalam controller ini karena rate limiting.
    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<String> handleRequestNotPermitted(RequestNotPermitted ex) {
        System.err.println("Rate limit exceeded for controller: " + ex.getMessage());
        // Anda bisa menambahkan header Retry-After di sini jika mau
        // response.getHeaders().add("Retry-After", "1"); // contoh 1 detik
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body("Too many requests for this service. Please try again later.");
    }

}