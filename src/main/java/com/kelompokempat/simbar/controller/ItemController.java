package com.kelompokempat.simbar.controller;

import com.kelompokempat.simbar.dto.ApiResponse;
import com.kelompokempat.simbar.dto.ItemDTO;
import com.kelompokempat.simbar.dto.PublicItemResponse;
import com.kelompokempat.simbar.entity.History; // Added
import com.kelompokempat.simbar.entity.Item;
import com.kelompokempat.simbar.repository.ItemRepository;
import com.kelompokempat.simbar.service.HistoryService; // Added
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor; // Added
import org.springframework.data.crossstore.ChangeSetPersister; // Added
import org.springframework.http.HttpStatus; // Added
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/items")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor // Added for constructor injection
@RateLimiter(name = "controllerWideLimiter")
public class ItemController {

    private final ItemRepository itemRepository; // Made final
    private final HistoryService historyService; // Added and made final

    // Public endpoint for landing page
    @GetMapping("/public/search")
    public ResponseEntity<ApiResponse<List<PublicItemResponse>>> searchItemsPublic(@RequestParam(required = false) String query) {
        List<Item> items;
        List<PublicItemResponse> publicItemResponseList;
        if (query != null && !query.trim().isEmpty()) {
            items = itemRepository.findByNameContainingIgnoreCaseOrCategoryContainingIgnoreCase(query, query);
        } else {
            items = itemRepository.findAll();
        }
        publicItemResponseList = items.stream()
                .map(item -> new PublicItemResponse(
                        item.getName(),
                        item.getCategory(),
                        item.getStock(),
                        item.getPrice()))
                .toList();
        return ResponseEntity.ok(new ApiResponse(true, "Items retrieved successfully", publicItemResponseList));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<ApiResponse<List<ItemDTO>>> getAllItems() { // Atau ItemListDTO
        List<Item> items = itemRepository.findAll();
        List<ItemDTO> itemDtos = items.stream()
                .map(item -> new ItemDTO(item.getId(), item.getName(), item.getCategory(), item.getStock(), item.getMinStock(), item.getPrice())) // Sesuaikan DTO
                .collect(Collectors.toList());
        return ResponseEntity.ok(new ApiResponse<>(true, "Items retrieved successfully", itemDtos));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<ApiResponse<Item>> getItem(@PathVariable Long id) { // Atau ItemDetailDTO
        Optional<Item> itemOpt = itemRepository.findById(id);
        if (itemOpt.isPresent()) {
            return ResponseEntity.ok(new ApiResponse<>(true, "Item retrieved successfully", itemOpt.get()));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(false, "Item not found", null));
    }


    @PostMapping
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<?> createItem(@Valid @RequestBody Item itemDetails) {
        try {
            // Create item with initial stock set to 0, other details from request
            Item newItem = new Item();
            newItem.setName(itemDetails.getName());
            newItem.setCategory(itemDetails.getCategory());
            newItem.setPrice(itemDetails.getPrice());
            newItem.setMinStock(itemDetails.getMinStock() != null ? itemDetails.getMinStock() : 1); // Default minStock
            newItem.setStock(0); // Initialize stock to 0

            Item savedItem = itemRepository.save(newItem);

            // If initial stock is provided in itemDetails and is > 0, record it via HistoryService
            if (itemDetails.getStock() != null && itemDetails.getStock() > 0) {
                History initialStockHistory = new History();
                initialStockHistory.setQuantity(itemDetails.getStock());
                initialStockHistory.setDescription("Initial stock on item creation");
                historyService.saveItemIn(savedItem.getId(), initialStockHistory);
            }

            // Re-fetch the item to get the final state including stock updated by HistoryService
            Item finalItemState = itemRepository.findById(savedItem.getId())
                    .orElseThrow(() -> new ChangeSetPersister.NotFoundException()); // Should not happen

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponse(true, "Item created successfully", finalItemState));
        } catch (ChangeSetPersister.NotFoundException e) {
            // This might occur if the item ID from saveItemIn somehow refers to a non-existent item,
            // or if saveItemIn itself throws it.
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Error processing item history: " + e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Error creating item: " + e.getMessage(), null));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<?> updateItem(@PathVariable Long id, @Valid @RequestBody Item itemDetails) {
        Optional<Item> itemOpt = itemRepository.findById(id);
        if (itemOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse(false, "Item not found", null));
        }

        Item item = itemOpt.get();
        int oldStock = item.getStock();

        // Update non-stock fields
        item.setName(itemDetails.getName());
        item.setCategory(itemDetails.getCategory());
        item.setMinStock(itemDetails.getMinStock());
        item.setPrice(itemDetails.getPrice());
        // Do NOT set item.setStock(itemDetails.getStock()) directly here.

        itemRepository.save(item); // Save non-stock changes first

        // Now handle stock changes through HistoryService
        Integer newStockTarget = itemDetails.getStock();
        if (newStockTarget != null) {
            int stockChange = newStockTarget - oldStock;
            try {
                if (stockChange > 0) {
                    History historyData = new History();
                    historyData.setQuantity(stockChange);
                    historyData.setDescription("Stock increased during item update");
                    historyService.saveItemIn(item.getId(), historyData);
                } else if (stockChange < 0) {
                    History historyData = new History();
                    historyData.setQuantity(Math.abs(stockChange));
                    historyData.setDescription("Stock decreased during item update");
                    historyService.saveItemOut(item.getId(), historyData);
                }
            } catch (ChangeSetPersister.NotFoundException e) {
                // Should not happen if item was found initially
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new ApiResponse(false, "Error updating item stock history: " + e.getMessage(), null));
            } catch (IllegalArgumentException e) {
                // e.g., insufficient stock from saveItemOut
                Item currentItemState = itemRepository.findById(id).orElse(item); // get current state
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, e.getMessage(), currentItemState));
            }
        }

        // Re-fetch the item to get its absolute final state after all operations
        Item updatedItem = itemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Failed to re-fetch item after update. ID: " + id)); // Should not happen

        return ResponseEntity.ok(new ApiResponse(true, "Item updated successfully", updatedItem));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<?> deleteItem(@PathVariable Long id) {
        if (itemRepository.existsById(id)) {
            itemRepository.deleteById(id);
            // Note: If Item entity has CascadeType.ALL on its histories collection,
            // associated history records will also be deleted. This is often desired.
            return ResponseEntity.ok(new ApiResponse(true, "Item deleted successfully", null));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND) // Changed to NOT_FOUND
                .body(new ApiResponse(false, "Item not found", null));
    }

    // Contoh jika ingin menggunakan DTO
    @GetMapping("/low-stock")
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<ApiResponse<List<ItemDTO>>> getLowStockItems() { // Atau DTO spesifik
        List<Item> items = itemRepository.findItemsWithStockLessThanOrEqualToMinStock();
        List<ItemDTO> itemDtos = items.stream()
                .map(item -> new ItemDTO(item.getId(), item.getName(), item.getCategory(), item.getStock(), item.getMinStock(), item.getPrice())) // Sesuaikan DTO
                .collect(Collectors.toList());
        return ResponseEntity.ok(new ApiResponse<>(true, "Low stock items retrieved successfully", itemDtos));
    }
    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<String> handleRequestNotPermitted(RequestNotPermitted ex) {
        System.err.println("Rate limit exceeded for controller: " + ex.getMessage());
        // Anda bisa menambahkan header Retry-After di sini jika mau
        // response.getHeaders().add("Retry-After", "1"); // contoh 1 detik
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body("Too many requests for this service. Please try again later.");
    }
}