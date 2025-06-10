package com.kelompokempat.simbar.dto;

import com.kelompokempat.simbar.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
public class JwtResponse {


    private String token;
    private String type;
    private String username;

    private Set<Role> roles;

}