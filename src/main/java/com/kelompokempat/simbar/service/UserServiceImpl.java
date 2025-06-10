package com.kelompokempat.simbar.service;

import com.kelompokempat.simbar.dto.UserDTO;
import com.kelompokempat.simbar.entity.Role;
import com.kelompokempat.simbar.entity.User;
import com.kelompokempat.simbar.repository.RoleRepository;
import com.kelompokempat.simbar.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    private final PasswordEncoder passwordEncoder;

    @Override
    public List<UserDTO> findAllUsers() {
        return userRepository.findAll().stream()
                .map(UserDTO::fromEntity) // Menggunakan metode factory di DTO
                .collect(Collectors.toList());
    }

    @Override
    public UserDTO findUserById(Long userId) throws UsernameNotFoundException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));
        return UserDTO.fromEntity(user);
    }

    @Override
    public User assignRole(Long userId, String roleName) throws UsernameNotFoundException, IllegalArgumentException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));

        user.getRoles().add(role);
        return userRepository.save(user);
    }

    @Override
    public User removeRole(Long userId, String roleName) throws UsernameNotFoundException, IllegalArgumentException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));

        if (!user.getRoles().contains(role)) {
            throw new IllegalArgumentException("User does not have role: " + roleName);
        }

        user.getRoles().remove(role);
        return userRepository.save(user);
    }


    @Override
    public User createUser(UserDTO userDto) {
        if (userRepository.findByUsername(userDto.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username already exists: " + userDto.getUsername());
        }
        User newUser = new User();
        newUser.setUsername(userDto.getUsername());
        newUser.setPassword(passwordEncoder.encode(userDto.getPassword())); // Pastikan password di-encode
        newUser.setAccountLocked(false); // Default tidak terkunci

        Set<Role> assignedRoles = new HashSet<>();
        if (userDto.getRoles() != null && !userDto.getRoles().isEmpty()) {
            for (String roleName : userDto.getRoles()) {
                Role role = roleRepository.findByName(roleName.toUpperCase()) // Cari berdasarkan nama (case-insensitive)
                        .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));
                assignedRoles.add(role);
            }
        } else {
            // Jika tidak ada peran yang dikirim, berikan peran default 'USER'
            Role defaultRole = roleRepository.findByName("USER")
                    .orElseThrow(() -> new IllegalStateException("Default role USER not found. Please ensure it exists."));
            assignedRoles.add(defaultRole);
        }
        newUser.setRoles(assignedRoles);
        return userRepository.save(newUser);
    }

    @Override
    public User updateUser(Long userId, UserDTO userDto) {
        User userToUpdate = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));

        // Update username jika ada dan berbeda, serta belum ada yang pakai
        if (userDto.getUsername() != null && !userDto.getUsername().equals(userToUpdate.getUsername())) {
            if (userRepository.findByUsername(userDto.getUsername()).filter(u -> !u.getId().equals(userId)).isPresent()) {
                throw new IllegalArgumentException("Username already exists: " + userDto.getUsername());
            }
            userToUpdate.setUsername(userDto.getUsername());
        }

        // Update password jika dikirim
        if (userDto.getPassword() != null && !userDto.getPassword().isEmpty()) {
            userToUpdate.setPassword(passwordEncoder.encode(userDto.getPassword()));
        }

        // Update roles jika ada
        if (userDto.getRoles() != null) { // Bisa jadi array kosong jika semua role di-uncheck
            Set<Role> assignedRoles = new HashSet<>();
            if (!userDto.getRoles().isEmpty()) {
                for (String roleName : userDto.getRoles()) {
                    Role role = roleRepository.findByName(roleName.toUpperCase())
                            .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));
                    assignedRoles.add(role);
                }
            }
            // Jika userDto.getRoles() adalah array kosong, maka userToUpdate.setRoles(assignedRoles) akan menghapus semua peran.
            // Jika Anda ingin minimal 1 peran, tambahkan validasi di sini atau di frontend.
            userToUpdate.setRoles(assignedRoles);
        }
        // Anda mungkin ingin menambahkan update untuk accountLocked juga jika diperlukan dari DTO
        // userToUpdate.setAccountLocked(userDto.isAccountLocked());

        return userRepository.save(userToUpdate);
    }

    @Override
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }


    @Override
    public User lockUserAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));
        user.setAccountLocked(true);
        user.setFailedAttempts(0);
        user.setLockTime(java.time.LocalDateTime.now());

        return userRepository.save(user);
    }

    @Override
    public User unlockUserAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));
        user.setAccountLocked(false);
        user.setFailedAttempts(0);
        user.setLockTime(null);
        return userRepository.save(user);
    }

}