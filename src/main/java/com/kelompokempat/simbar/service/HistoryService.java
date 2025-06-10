package com.kelompokempat.simbar.service;

import com.kelompokempat.simbar.dto.HistoryResponseDTO;
import com.kelompokempat.simbar.dto.ItemSummaryDTO;
import com.kelompokempat.simbar.dto.StockUpdateRequestBulk;
import com.kelompokempat.simbar.entity.History;
import com.kelompokempat.simbar.entity.Item;
import com.kelompokempat.simbar.entity.TypeEnum;
import com.kelompokempat.simbar.repository.HistoryRepository;
import com.kelompokempat.simbar.repository.ItemRepository;
import com.kelompokempat.simbar.spec.HistorySpecification;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class HistoryService {

    private final HistoryRepository historyRepository;
    private final ItemRepository itemRepository;

    // CREATE - Stock IN (Optimized)
    public History saveItemIn(Long itemId, History historyData) throws ChangeSetPersister.NotFoundException {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ChangeSetPersister.NotFoundException());

        // Update stock directly in Item entity - Single DB operation
        int newStock = item.getStock() + historyData.getQuantity();
        item.setStock(newStock);

        // Create history record for audit trail
        History history = new History();
        history.setItem(item);
        history.setType(TypeEnum.IN);
        history.setQuantity(historyData.getQuantity());
        history.setDescription(historyData.getDescription());
        history.setCreatedAt(LocalDateTime.now());

        // Save both in single transaction
        itemRepository.save(item);
        return historyRepository.save(history);
    }

    // CREATE - Stock OUT (Optimized)
    public History saveItemOut(Long itemId, History historyData) throws ChangeSetPersister.NotFoundException {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ChangeSetPersister.NotFoundException());

        // Check stock availability
        if (item.getStock() < historyData.getQuantity()) {
            throw new IllegalArgumentException("Insufficient stock. Available: " + item.getStock());
        }

        // Update stock directly - Single DB operation
        int newStock = item.getStock() - historyData.getQuantity();
        item.setStock(newStock);

        // Create history record for audit trail
        History history = new History();
        history.setItem(item);
        history.setType(TypeEnum.OUT);
        history.setQuantity(historyData.getQuantity());
        history.setDescription(historyData.getDescription());
        history.setCreatedAt(LocalDateTime.now());

        // Save both in single transaction
        itemRepository.save(item);
        return historyRepository.save(history);
    }

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

    // READ - Get histories with pagination (For large datasets)
    public Page<HistoryResponseDTO> getAllHistoriesPaginated(int page, int size, String sortBy, Sort.Direction direction) {
        return getAllHistoriesPaginatedWithFilters(page, size, sortBy, direction, null, null, null, null);
    }


    public Page<HistoryResponseDTO> getAllHistoriesPaginatedWithFilters(
            int page, int size, String sortBy, Sort.Direction direction,
            TypeEnum type, String itemName, LocalDate startDate, LocalDate endDate) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        // Bangun spesifikasi gabungan
        Specification<History> spec = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();// Mulai dengan spesifikasi netral

        if (type != null) {
            spec = spec.and(HistorySpecification.filterByType(type));
        }
        if (itemName != null && !itemName.trim().isEmpty()) {
            spec = spec.and(HistorySpecification.filterByItemName(itemName));
        }
        if (startDate != null || endDate != null) {
            spec = spec.and(HistorySpecification.filterByDateRange(startDate, endDate));
        }
        // Tambahkan filter lain jika perlu

        Page<History> historyPage = historyRepository.findAll(spec, pageable); // Gunakan findAll dengan Specification

        List<HistoryResponseDTO> dtoList = historyPage.getContent().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        return new PageImpl<>(dtoList, pageable, historyPage.getTotalElements());
    }


    // READ - Get history by ID (Simple lookup)
    public HistoryResponseDTO getHistoryByIdAsDto(Long id) throws ChangeSetPersister.NotFoundException {
        History history = historyRepository.findById(id)
                .orElseThrow(() -> new ChangeSetPersister.NotFoundException());
        return convertToDto(history);
    }

    // READ - Get histories by item with pagination
    public Page<HistoryResponseDTO> getHistoriesByItemIdPaginated(Long itemId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<History> historyPage = historyRepository.findByItemId(itemId, pageable);
        List<HistoryResponseDTO> dtoList = historyPage.getContent().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        return new PageImpl<>(dtoList, pageable, historyPage.getTotalElements());
    }

    // READ - Get recent histories (Limited to prevent memory issues) - Bisa juga diubah ke DTO
    public List<HistoryResponseDTO> getRecentHistories(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<History> histories = historyRepository.findAll(pageable).getContent();
        return histories.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    // READ - Get count of histories by item (Performance metric)
    public long getHistoryCountByItemId(Long itemId) {
        return historyRepository.countByItemId(itemId);
    }

    // UPDATE - Update history description only
    public History updateHistory(Long id, String newDescription) throws ChangeSetPersister.NotFoundException {
        History history = historyRepository.findById(id)
                .orElseThrow(() -> new ChangeSetPersister.NotFoundException());

        history.setDescription(newDescription);
        history.setUpdatedAt(LocalDateTime.now());

        return historyRepository.save(history);
    }

    // DELETE - Delete history (DO NOT reverse stock - stock is authoritative in Item)
    public void deleteHistory(Long id) throws ChangeSetPersister.NotFoundException {
        if (!historyRepository.existsById(id)) {
            throw new ChangeSetPersister.NotFoundException();
        }
        historyRepository.deleteById(id);
        // Note: We don't reverse stock changes - Item.stock is the single source of truth
    }

    // DELETE - Bulk delete old histories (Maintenance operation)
    public void deleteOldHistories(LocalDateTime beforeDate) {
        historyRepository.deleteByCreatedAtBefore(beforeDate);
    }

    // UTILITY - Get current stock (Direct from Item - no calculation needed)
    public int getCurrentStock(Long itemId) throws ChangeSetPersister.NotFoundException {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ChangeSetPersister.NotFoundException());
        return item.getStock(); // Direct access - no calculation needed!
    }

    // UTILITY - Get stock statistics (Cached/Aggregated queries)
    public StockStatistics getStockStatistics(Long itemId) {
        // Use database aggregation instead of loading all records
        Integer totalIn = historyRepository.sumQuantityByItemIdAndType(itemId, TypeEnum.IN);
        Integer totalOut = historyRepository.sumQuantityByItemIdAndType(itemId, TypeEnum.OUT);
        Long currentStock = 0L;

        if (totalIn != null) {
            currentStock += totalIn;
        }
        if (totalOut != null) {
            currentStock -= totalOut;
        }

        return new StockStatistics(
                totalIn != null ? totalIn : 0,
                totalOut != null ? totalOut : 0,
                currentStock
        );
    }


    @Transactional // Pastikan ini adalah org.springframework.transaction.annotation.Transactional
    public void processBulkUpdateFile(MultipartFile file) throws IOException {
        List<StockUpdateRequestBulk> requests;
        String originalFilename = file.getOriginalFilename();

        if (originalFilename != null && (originalFilename.endsWith(".xlsx") || originalFilename.endsWith(".xls"))) {
            requests = parseExcelFile(file.getInputStream());
        } else if (originalFilename != null && originalFilename.endsWith(".csv")) {
            requests = parseCsvFile(file.getInputStream());
        } else {
            throw new IllegalArgumentException("Unsupported file format. Please upload an Excel (.xlsx, .xls) or CSV (.csv) file.");
        }

        if (requests.isEmpty()) {
            throw new IllegalArgumentException("No valid data found in the uploaded file or the file is empty.");
        }

        // Panggil metode bulk update yang sudah ada
        this.bulkStockUpdate(requests);
    }

    private List<StockUpdateRequestBulk> parseExcelFile(InputStream inputStream) throws IOException {
        List<StockUpdateRequestBulk> requests = new ArrayList<>();
        // Gunakan WorkbookFactory untuk menangani .xls dan .xlsx secara otomatis
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0); // Ambil sheet pertama
            Iterator<Row> rowIterator = sheet.iterator();

            // Lewati baris header jika ada (asumsi baris pertama adalah header)
            if (rowIterator.hasNext()) {
                rowIterator.next();
            }

            int rowNum = 1; // Untuk pelaporan error
            while (rowIterator.hasNext()) {
                rowNum++;
                Row currentRow = rowIterator.next();
                StockUpdateRequestBulk request = new StockUpdateRequestBulk();
                try {
                    // Kolom A: itemName (String)
                    Cell itemNameCell = currentRow.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (itemNameCell != null && itemNameCell.getCellType() == CellType.STRING) {
                        request.setItemName(itemNameCell.getStringCellValue().trim());
                    } else if (itemNameCell != null && itemNameCell.getCellType() == CellType.NUMERIC) {
                        // Handle jika nama item tidak sengaja terformat sebagai angka di Excel
                        request.setItemName(String.valueOf((long) itemNameCell.getNumericCellValue()));
                    } else {
                        // Anda bisa melempar error atau mencatatnya dan melanjutkan
                        // throw new IllegalArgumentException("Row " + rowNum + ": Item name is missing or not a string.");
                        System.err.println("Warning: Row " + rowNum + ": Item name is missing or not a string. Skipping row.");
                        continue;
                    }

                    // Kolom B: quantity (Numeric)
                    Cell quantityCell = currentRow.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (quantityCell != null && quantityCell.getCellType() == CellType.NUMERIC) {
                        request.setQuantity((int) quantityCell.getNumericCellValue());
                    } else {
                        // throw new IllegalArgumentException("Row " + rowNum + ": Quantity is missing or not a number for item " + request.getItemName());
                        System.err.println("Warning: Row " + rowNum + ": Quantity is missing or not a number for item " + request.getItemName() + ". Skipping row.");
                        continue;
                    }

                    // Kolom C: type (String - IN/OUT)
                    Cell typeCell = currentRow.getCell(2, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (typeCell != null && typeCell.getCellType() == CellType.STRING) {
                        String typeStr = typeCell.getStringCellValue().trim().toUpperCase();
                        try {
                            request.setType(TypeEnum.valueOf(typeStr));
                        } catch (IllegalArgumentException e) {
                            // throw new IllegalArgumentException("Row " + rowNum + ": Invalid type '" + typeStr + "' for item " + request.getItemName());
                            System.err.println("Warning: Row " + rowNum + ": Invalid type '" + typeStr + "' for item " + request.getItemName() + ". Skipping row.");
                            continue;
                        }
                    } else {
                        // throw new IllegalArgumentException("Row " + rowNum + ": Type is missing or not a string for item " + request.getItemName());
                        System.err.println("Warning: Row " + rowNum + ": Type is missing or not a string for item " + request.getItemName() + ". Skipping row.");
                        continue;
                    }

                    // Kolom D: description (String, Opsional)
                    Cell descriptionCell = currentRow.getCell(3, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (descriptionCell != null && descriptionCell.getCellType() == CellType.STRING) {
                        request.setDescription(descriptionCell.getStringCellValue().trim());
                    } else {
                        request.setDescription(null); // Atau string kosong ""
                    }
                    requests.add(request);
                } catch (Exception e) {
                    // Log error untuk baris spesifik dan lanjutkan jika memungkinkan
                    System.err.println("Error processing row " + rowNum + " from Excel: " + e.getMessage() + ". Skipping row.");
                    // Anda bisa mengumpulkan error ini untuk dilaporkan kembali
                }
            }
        }
        return requests;
    }

    private List<StockUpdateRequestBulk> parseCsvFile(InputStream inputStream) throws IOException {
        List<StockUpdateRequestBulk> requests = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            boolean isHeader = true; // Asumsi baris pertama adalah header
            int rowNum = 0;

            while ((line = br.readLine()) != null) {
                rowNum++;
                if (isHeader) {
                    isHeader = false;
                    continue; // Lewati baris header
                }

                // Asumsi pemisah CSV adalah koma (,)
                // Anda mungkin perlu library CSV yang lebih canggih jika ada kasus edge seperti koma di dalam field
                String[] values = line.split(",");
                if (values.length < 3) { // Minimal itemName, quantity, type
                    System.err.println("Warning: Row " + rowNum + ": Insufficient columns. Expected at least 3. Skipping row.");
                    continue;
                }

                StockUpdateRequestBulk request = new StockUpdateRequestBulk();
                try {
                    request.setItemName(values[0].trim());
                    request.setQuantity(Integer.parseInt(values[1].trim()));
                    request.setType(TypeEnum.valueOf(values[2].trim().toUpperCase()));

                    if (values.length > 3 && StringUtils.hasText(values[3])) {
                        request.setDescription(values[3].trim());
                    } else {
                        request.setDescription(null);
                    }
                    requests.add(request);
                } catch (NumberFormatException e) {
                    System.err.println("Warning: Row " + rowNum + ": Invalid quantity format for item " + values[0] + ". Skipping row.");
                } catch (IllegalArgumentException e) {
                    System.err.println("Warning: Row " + rowNum + ": Invalid type for item " + values[0] + ". Skipping row.");
                } catch (Exception e) {
                    System.err.println("Error processing row " + rowNum + " from CSV: " + e.getMessage() + ". Skipping row.");
                }
            }
        }
        return requests;
    }


    @Transactional // Pastikan ini adalah org.springframework.transaction.annotation.Transactional
    public void bulkStockUpdate(List<StockUpdateRequestBulk> requests) {
        List<String> errors = new ArrayList<>();

        for (StockUpdateRequestBulk request : requests) {
            // Validasi dasar yang mungkin sudah dilakukan saat parsing, tapi baik untuk double check
            if (!StringUtils.hasText(request.getItemName())) {
                errors.add("Item name is missing for one of the requests.");
                continue;
            }
            if (request.getQuantity() == null || request.getQuantity() <= 0) {
                errors.add("Invalid quantity for item '" + request.getItemName() + "'. Quantity must be positive.");
                continue;
            }
            if (request.getType() == null) {
                errors.add("Transaction type (IN/OUT) is missing for item '" + request.getItemName() + "'.");
                continue;
            }

            try {
                Optional<Item> itemOpt = itemRepository.findByNameIgnoreCase(request.getItemName());

                if (itemOpt.isEmpty()) {
                    errors.add("Item with name '" + request.getItemName() + "' not found.");
                    continue;
                }

                Item item = itemOpt.get();
                History historyData = createHistoryFromBulkRequest(request);

                if (request.getType() == TypeEnum.IN) {
                    saveItemIn(item.getId(), historyData);
                } else { // TypeEnum.OUT
                    saveItemOut(item.getId(), historyData);
                }
            } catch (IllegalArgumentException e) {
                errors.add("Failed to update stock for item '" + request.getItemName() + "': " + e.getMessage());
            } catch (ChangeSetPersister.NotFoundException e) {
                errors.add("An unexpected error occurred finding item '" + request.getItemName() + "' during stock update.");
            } catch (Exception e) {
                errors.add("An unexpected error occurred for item '" + request.getItemName() + "': " + e.getMessage());
            }
        }

        if (!errors.isEmpty()) {
            throw new RuntimeException("Bulk update process encountered errors: \n" + String.join("\n", errors));
        }
    }

    // Helper method
    private History createHistoryFromBulkRequest(StockUpdateRequestBulk request) {
        History history = new History();
        history.setQuantity(request.getQuantity());
        history.setDescription(request.getDescription());
        // Tipe dan Item akan di-set di saveItemIn/saveItemOut
        return history;
    }


    public static class StockStatistics {
        private final int totalIn;
        private final int totalOut;
        private final long currentStock;

        public StockStatistics(int totalIn, int totalOut, long currentStock) {
            this.totalIn = totalIn;
            this.totalOut = totalOut;
            this.currentStock = currentStock;
        }

        public int getTotalIn() {
            return totalIn;
        }

        public int getTotalOut() {
            return totalOut;
        }

        public long getCurrentStock() {
            return currentStock;
        }
    }
}