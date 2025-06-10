package com.kelompokempat.simbar.repository;

import com.kelompokempat.simbar.entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {
    List<Item> findByNameContainingIgnoreCaseOrCategoryContainingIgnoreCase(String name, String category);

    @Query("SELECT i FROM Item i WHERE i.stock <= i.minStock")
    List<Item> findItemsWithStockLessThanOrEqualToMinStock();

    Optional<Item> findByNameIgnoreCase(String itemName);
}