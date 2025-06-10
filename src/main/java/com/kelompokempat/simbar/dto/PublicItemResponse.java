package com.kelompokempat.simbar.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PublicItemResponse {

    private String name;
    private String category;
    private Integer stock;
    private Long price;

}
