package com.kelompokempat.simbar.controller;

import com.kelompokempat.simbar.dto.ApiResponse; // Import ApiResponse
import com.kelompokempat.simbar.dto.HistoryResponseDTO;
import com.kelompokempat.simbar.dto.ItemSummaryDTO;
import com.kelompokempat.simbar.entity.History;
import com.kelompokempat.simbar.entity.TypeEnum;
import com.kelompokempat.simbar.service.HistoryService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/histories")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class HistoryController {

    private final HistoryService historyService;

    private static final String ITEM_NOT_FOUND = "Item not found";

    private HistoryResponseDTO convertToDto(History history) {
        ItemSummaryDTO itemSummary = null;
        if (history.getItem() != null) {
            itemSummary = new ItemSummaryDTO(history.getItem().getId(), history.getItem().getName());
        }
        return new HistoryResponseDTO(
                history.getId(),
                history.getQuantity(),
                history.getType(),
                history.getDescription(),
                history.getCreatedAt(),
                history.getUpdatedAt(),
                itemSummary
        );
    }


    @PreAuthorize("hasAuthority('USER')")
    @PostMapping("/items/{itemId}/in")
    public ResponseEntity<ApiResponse<HistoryResponseDTO>> addStockIn(@PathVariable Long itemId, @RequestBody History history) {
        try {
            History savedHistory = historyService.saveItemIn(itemId, history);
            // Anda perlu metode di service untuk konversi atau lakukan di sini
            HistoryResponseDTO dto = convertToDto(savedHistory); // Asumsi ada helper convertToDto
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponse<>(true, "Stock in operation recorded", dto));
        } catch (ChangeSetPersister.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, ITEM_NOT_FOUND, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, "Invalid stock in operation: " + e.getMessage(), null));
        }
    }

    @PreAuthorize("hasAuthority('USER')")
    @PostMapping("/items/{itemId}/out")
    public ResponseEntity<ApiResponse<HistoryResponseDTO>> addStockOut(@PathVariable Long itemId, @RequestBody History history) { // RequestBody bisa juga DTO
        try {
            History savedHistory = historyService.saveItemOut(itemId, history);
            HistoryResponseDTO dto = convertToDto(savedHistory);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponse<>(true, "Stock in operation recorded", dto));
        } catch (ChangeSetPersister.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, ITEM_NOT_FOUND, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, "Invalid stock in operation: " + e.getMessage(), null));
        }
    }

    // BULK CREATE - For high volume
    @PreAuthorize("hasAuthority('USER')")
    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<String>> bulkStockUpdateFromFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, "File cannot be empty.", null));
        }

        // Validasi tipe file (opsional tapi direkomendasikan)
        String contentType = file.getContentType();
        if (contentType == null ||
                (!contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") && // .xlsx
                        !contentType.equals("application/vnd.ms-excel") && // .xls
                        !contentType.equals("text/csv"))) { // .csv
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, "Invalid file type. Only Excel (.xlsx, .xls) and CSV (.csv) files are allowed.", null));
        }

        try {
            // Panggil metode service yang baru untuk memproses file
            historyService.processBulkUpdateFile(file);
            return ResponseEntity.ok(new ApiResponse<>(true, "Bulk update from file processed successfully.", "Check service logs for details on individual records."));
        } catch (IllegalArgumentException e) { // Menangkap error validasi dari service (misal, format file salah)
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (RuntimeException e) { // Menangkap error umum dari service (misal, error saat parsing atau update)
            // Memberikan detail error yang lebih spesifik jika memungkinkan
            String errorMessage = "Bulk update failed during processing";
            if (e.getMessage() != null && !e.getMessage().isEmpty()) {
                errorMessage += ": " + e.getMessage();
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, errorMessage, null));
        } catch (Exception e) { // Fallback untuk error tak terduga lainnya
            // log.error("Unexpected error during bulk file processing: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "An unexpected error occurred while processing the file.", null));
        }
    }

    // READ - Paginated for large datasets
    @GetMapping
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<ApiResponse<Page<HistoryResponseDTO>>> getAllHistories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @RequestParam(required = false) String type, // Filter type (IN/OUT)
            @RequestParam(required = false) String itemName, // Filter nama item
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, // Filter tanggal mulai
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate // Filter tanggal akhir
    ) {
        Sort.Direction direction = sortDirection.equalsIgnoreCase("ASC") ? Sort.Direction.ASC : Sort.Direction.DESC;
        TypeEnum filterTypeEnum = null;
        if (type != null && !type.trim().isEmpty()) {
            try {
                filterTypeEnum = TypeEnum.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(new ApiResponse<>(false, "Invalid type filter: " + type, null));
            }
        }

        return ResponseEntity.ok(new ApiResponse<>(true, "History records retrieved",
                historyService.getAllHistoriesPaginatedWithFilters(page, size, sortBy, direction, filterTypeEnum, itemName, startDate, endDate)));
    }


    @GetMapping("/items/{itemId}")
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<ApiResponse<Page<HistoryResponseDTO>>> getHistoriesByItemId(
                                                                                       @PathVariable Long itemId,
                                                                                       @RequestParam(defaultValue = "0") int page,
                                                                                       @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(new ApiResponse<>(true, "History records for item retrieved", historyService.getHistoriesByItemIdPaginated(itemId, page, size)));
    }

    // READ - Current stock (direct from Item)
    @GetMapping("/items/{itemId}/current-stock")
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<ApiResponse<Integer>> getCurrentStock(@PathVariable Long itemId) {
        try {

            return ResponseEntity.ok(new ApiResponse<>(true, "Current stock retrieved", historyService.getCurrentStock(itemId))); // Use ApiResponse
        } catch (ChangeSetPersister.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, ITEM_NOT_FOUND, null)); // Use ApiResponse
        }
    }

    // READ - Statistics (aggregated)
    @GetMapping("/items/{itemId}/statistics")
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<ApiResponse<HistoryService.StockStatistics>> getStockStatistics(@PathVariable Long itemId) {

        return ResponseEntity.ok(new ApiResponse<>(true, "Stock statistics retrieved", historyService.getStockStatistics(itemId))); // Use ApiResponse
    }

    // UPDATE - Limited to description only
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER')")

    public ResponseEntity<ApiResponse<HistoryResponseDTO>> updateHistory(@PathVariable Long id, @RequestBody String description) {
        try {
            History updatedHistory = historyService.updateHistory(id, description);
            // Anda perlu metode di service untuk konversi atau lakukan di sini
            HistoryResponseDTO dto = convertToDto(updatedHistory); // Asumsi ada helper convertToDto
            return ResponseEntity.ok(new ApiResponse<>(true, "History description updated", dto));
        } catch (ChangeSetPersister.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, "History record not found", null));
        }
    }


    // DELETE - Safe deletion (doesn't affect stock)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<ApiResponse<Void>> deleteHistory(@PathVariable Long id) {
        try {
            historyService.deleteHistory(id);
            return ResponseEntity.ok(new ApiResponse<>(true, "History record deleted", null)); // Use ApiResponse
        } catch (ChangeSetPersister.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, "History record not found", null)); // Use ApiResponse
        }
    }


}