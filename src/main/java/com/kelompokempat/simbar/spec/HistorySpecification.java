package com.kelompokempat.simbar.spec; // atau package yang sesuai
import com.kelompokempat.simbar.entity.History;
import com.kelompokempat.simbar.entity.Item; // Jika filter berdasarkan nama item
import com.kelompokempat.simbar.entity.TypeEnum;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils; // untuk StringUtils.hasText

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class HistorySpecification {

    public static Specification<History> filterByType(TypeEnum type) {
        return (root, query, criteriaBuilder) -> {
            if (type == null) {
                return criteriaBuilder.conjunction(); // jika null, tidak ada filter
            }
            return criteriaBuilder.equal(root.get("type"), type);
        };
    }

    public static Specification<History> filterByItemName(String itemName) {
        return (root, query, criteriaBuilder) -> {
            if (!StringUtils.hasText(itemName)) {
                return criteriaBuilder.conjunction();
            }
            // Untuk join dengan tabel Item dan filter berdasarkan nama item
            Join<History, Item> itemJoin = root.join("item", JoinType.INNER);
            return criteriaBuilder.like(criteriaBuilder.lower(itemJoin.get("name")), "%" + itemName.toLowerCase() + "%");
        };
    }

    public static Specification<History> filterByDateRange(LocalDate startDate, LocalDate endDate) {
        return (root, query, criteriaBuilder) -> {
            Predicate predicate = criteriaBuilder.conjunction();
            if (startDate != null) {
                LocalDateTime startOfDay = startDate.atStartOfDay();
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startOfDay));
            }
            if (endDate != null) {
                LocalDateTime endOfDay = endDate.atTime(LocalTime.MAX);
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), endOfDay));
            }
            return predicate;
        };
    }
    // Anda bisa menambahkan lebih banyak metode spesifikasi di sini
}
    