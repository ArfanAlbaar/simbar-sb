package com.kelompokempat.simbar.config;

import com.kelompokempat.simbar.entity.Role;
import com.kelompokempat.simbar.entity.User;
import com.kelompokempat.simbar.repository.RoleRepository; // Add RoleRepository import
import com.kelompokempat.simbar.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seedUser(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) { // Add RoleRepository
        return args -> {
            String defaultUsername = "admin";
            String defaultPassword = "admin123";
            String adminRoleName = "ADMIN";

            // Ensure the admin role exists
            Optional<Role> adminRoleOptional = roleRepository.findByName(adminRoleName);
            Role adminRole;
            if (adminRoleOptional.isEmpty()) {
                adminRole = new Role();
                adminRole.setName(adminRoleName);
                adminRole = roleRepository.save(adminRole);
                System.out.println("✅ Role '" + adminRoleName + "' created.");
            } else {
                adminRole = adminRoleOptional.get();
                System.out.println("ℹ️ Role '" + adminRoleName + "' already exists.");
            }

            // Check if admin user exists
            if (userRepository.findByUsername(defaultUsername).isEmpty()) {
                User admin = new User();
                admin.setUsername(defaultUsername);
                admin.setPassword(passwordEncoder.encode(defaultPassword));

                Set<Role> roles = new HashSet<>();
                roles.add(adminRole);
                admin.setRoles(roles);

                userRepository.save(admin);
                System.out.println("✅ Default admin user created: " + defaultUsername + " with role " + adminRoleName);
            } else {
                System.out.println("ℹ️ Default admin user already exists.");
            }
        };
    }
}