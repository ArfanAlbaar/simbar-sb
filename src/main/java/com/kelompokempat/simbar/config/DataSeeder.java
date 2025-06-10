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
    CommandLineRunner seedUser(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            // --- Membuat Role ADMIN ---
            Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> {
                Role newRole = new Role();
                newRole.setName("ADMIN");
                System.out.println("✅ Role 'ADMIN' created.");
                return roleRepository.save(newRole);
            });

            // --- TAMBAHKAN INI: Membuat Role USER ---
            Role userRole = roleRepository.findByName("USER").orElseGet(() -> {
                Role newRole = new Role();
                newRole.setName("USER");
                System.out.println("✅ Role 'USER' created.");
                return roleRepository.save(newRole);
            });

            // --- Membuat User Admin ---
            String defaultUsername = "admin";
            if (userRepository.findByUsername(defaultUsername).isEmpty()) {
                User admin = new User();
                admin.setUsername(defaultUsername);
                admin.setPassword(passwordEncoder.encode("admin123"));

                Set<Role> roles = new HashSet<>();
                roles.add(adminRole);
                roles.add(userRole); // <<< TAMBAHKAN role 'USER' ke set
                admin.setRoles(roles);

                userRepository.save(admin);
                System.out.println("✅ Default admin user created with roles: ADMIN, USER");
            } else {
                System.out.println("ℹ️ Default admin user already exists.");
            }
        };
    }
}