package com.kelompokempat.simbar.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long id;
    private String username;
    private String password;
    private boolean accountLocked;
    private Set<String> roles; // Kita akan mengirim nama peran sebagai String

    // Anda bisa menambahkan konstruktor atau metode factory jika diperlukan
    // Misalnya, untuk konversi dari User entity ke UserDTO
    public static UserDTO fromEntity(com.kelompokempat.simbar.entity.User userEntity) {
        UserDTO dto = new UserDTO();
        dto.setId(userEntity.getId());
        dto.setUsername(userEntity.getUsername());
        dto.setAccountLocked(userEntity.isAccountLocked());
        if (userEntity.getRoles() != null) {
            dto.setRoles(userEntity.getRoles().stream()
                    .map(com.kelompokempat.simbar.entity.Role::getName)
                    .collect(Collectors.toSet()));
        }
        return dto;
    }
}