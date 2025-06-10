package com.kelompokempat.simbar.repository;

import com.kelompokempat.simbar.entity.History;
import com.kelompokempat.simbar.entity.TypeEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HistoryRepository extends JpaRepository<History, Long>, JpaSpecificationExecutor<History> {

    // Paginated queries for large datasets
    Page<History> findByItemId(Long itemId, Pageable pageable);

    // Count queries for statistics
    long countByItemId(Long itemId);

    // Aggregation queries - much faster than loading all records
    @Query("SELECT SUM(h.quantity) FROM History h WHERE h.item.id = :itemId AND h.type = :type")
    Integer sumQuantityByItemIdAndType(@Param("itemId") Long itemId, @Param("type") TypeEnum type);

    // Bulk delete for maintenance
    @Modifying
    @Query("DELETE FROM History h WHERE h.createdAt < :beforeDate")
    void deleteByCreatedAtBefore(@Param("beforeDate") LocalDateTime beforeDate);

    // Check if history exists (for validation)
    boolean existsByItemId(Long itemId);
}
