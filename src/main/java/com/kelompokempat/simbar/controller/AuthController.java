package com.kelompokempat.simbar.controller;

import com.kelompokempat.simbar.dto.ApiResponse;
import com.kelompokempat.simbar.dto.JwtResponse;
import com.kelompokempat.simbar.dto.LoginRequest;
import com.kelompokempat.simbar.entity.User;
import com.kelompokempat.simbar.repository.UserRepository;
import com.kelompokempat.simbar.security.JwtTokenUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JwtTokenUtil jwtUtils;

    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final long LOCK_TIME_MINUTES = 5;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            Optional<User> userOpt = userRepository.findByUsername(loginRequest.getUsername());

            if (userOpt.isPresent()) {
                User user = userOpt.get();

                // Check if account is locked
                if (user.isAccountLocked()) {
                    if (user.getLockTime().plusMinutes(LOCK_TIME_MINUTES).isAfter(LocalDateTime.now())) {
                        return ResponseEntity.badRequest()
                                .body(new ApiResponse(false, "Account locked. Try again later.", "Account is locked due to too many failed login attempts"));
                    } else {
                        // Unlock account
                        user.setAccountLocked(false);
                        user.setFailedAttempts(0);
                        user.setLockTime(null);
                        userRepository.save(user);
                    }
                }

                // Authenticate
                Authentication authentication = authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
                );

                // Reset failed attempts on successful login
                if (user.getFailedAttempts() > 0) {
                    user.setFailedAttempts(0);
                    userRepository.save(user);
                }

                String jwt = jwtUtils.generateToken(loginRequest.getUsername());
                return ResponseEntity.ok(new ApiResponse(true, "Login successful", new JwtResponse(jwt, "Bearer", loginRequest.getUsername(), userOpt.get().getRoles())));

            } else {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Invalid credentials", "User not found"));
            }

        } catch (BadCredentialsException e) {
            // Handle failed login attempt
            Optional<User> userOpt = userRepository.findByUsername(loginRequest.getUsername());
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                user.setFailedAttempts(user.getFailedAttempts() + 1);

                if (user.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
                    user.setAccountLocked(true);
                    user.setLockTime(LocalDateTime.now());
                }

                userRepository.save(user);
            }

            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Invalid credentials", "Incorrect username or password"));
        }
    }

}
