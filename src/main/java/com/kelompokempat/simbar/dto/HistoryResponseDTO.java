package com.kelompokempat.simbar.dto;

import com.kelompokempat.simbar.entity.TypeEnum;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class HistoryResponseDTO {
    private Long id;
    private Integer quantity;
    private TypeEnum type;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private ItemSummaryDTO item; // Menggunakan DTO ringkasan untuk item

    public HistoryResponseDTO(Long id, Integer quantity, TypeEnum type, String description,
                              LocalDateTime createdAt, LocalDateTime updatedAt, ItemSummaryDTO item) {
        this.id = id;
        this.quantity = quantity;
        this.type = type;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.item = item;
    }
}