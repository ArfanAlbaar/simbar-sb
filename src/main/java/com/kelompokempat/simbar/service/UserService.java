package com.kelompokempat.simbar.service;

import com.kelompokempat.simbar.dto.UserDTO;
import com.kelompokempat.simbar.entity.User; // Impor User entity
import org.springframework.security.core.userdetails.UsernameNotFoundException; // Impor exception

import java.util.List;

public interface UserService {
    List<UserDTO> findAllUsers();

    UserDTO findUserById(Long userId) throws UsernameNotFoundException; // Tambahkan metode ini jika diperlukan

    User assignRole(Long userId, String roleName) throws UsernameNotFoundException, IllegalArgumentException;

    User removeRole(Long userId, String roleName) throws UsernameNotFoundException, IllegalArgumentException; // Contoh metode tambahan

    User createUser(UserDTO userCreationDto);

    User updateUser(Long userId, UserDTO userUpdateDto);

    void deleteUser(Long userId);

    User lockUserAccount(Long userId);

    User unlockUserAccount(Long userId);
}