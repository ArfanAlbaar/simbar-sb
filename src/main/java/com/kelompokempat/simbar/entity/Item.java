package com.kelompokempat.simbar.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "items")
@Getter
@Setter
@RequiredArgsConstructor
public class Item {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @NotBlank(message = "Item name is required")
    @Size(max = 100)
    private String name;

    @Column(nullable = false)
    @NotBlank(message = "Category is required")
    @Size(max = 50)
    private String category;

    @Column(nullable = false)
    @Min(value = 0, message = "Stock cannot be negative")
    private Integer stock;

    @Column(name = "min_stock")
    @Min(value = 0, message = "Minimum stock cannot be negative")
    private Integer minStock = 1;

    @Column(nullable = false)
    @Min(value = 0, message = "Price cannot be negative")
    private Long price;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @JsonIgnore
    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<History> histories;
}
