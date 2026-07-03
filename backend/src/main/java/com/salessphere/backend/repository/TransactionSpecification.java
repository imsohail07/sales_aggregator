package com.salessphere.backend.repository;

import com.salessphere.backend.entity.Category;
import com.salessphere.backend.entity.Region;
import com.salessphere.backend.entity.Transaction;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TransactionSpecification {

    public static Specification<Transaction> getFilterSpecification(
            String search, String region, String category,
            LocalDate startDate, LocalDate endDate,
            Long minAmount, Long maxAmount) {

        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Global search (matches code, region name, or category name)
            if (StringUtils.hasText(search)) {
                String likePattern = "%" + search.trim().toLowerCase() + "%";
                Join<Transaction, Region> regionJoin = root.join("region");
                Join<Transaction, Category> categoryJoin = root.join("category");

                Predicate codePredicate = criteriaBuilder.like(criteriaBuilder.lower(root.get("transactionCode")), likePattern);
                Predicate regionPredicate = criteriaBuilder.like(criteriaBuilder.lower(regionJoin.get("name")), likePattern);
                Predicate categoryPredicate = criteriaBuilder.like(criteriaBuilder.lower(categoryJoin.get("name")), likePattern);

                predicates.add(criteriaBuilder.or(codePredicate, regionPredicate, categoryPredicate));
            }

            // Region exact filter
            if (StringUtils.hasText(region)) {
                Join<Transaction, Region> regionJoin = root.join("region");
                predicates.add(criteriaBuilder.equal(criteriaBuilder.lower(regionJoin.get("name")), region.trim().toLowerCase()));
            }

            // Category exact filter
            if (StringUtils.hasText(category)) {
                Join<Transaction, Category> categoryJoin = root.join("category");
                predicates.add(criteriaBuilder.equal(criteriaBuilder.lower(categoryJoin.get("name")), category.trim().toLowerCase()));
            }

            // Date range filter
            if (startDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("transactionDate"), startDate.atStartOfDay()));
            }
            if (endDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("transactionDate"), endDate.atTime(java.time.LocalTime.MAX)));
            }

            // Amount range filter (cents)
            if (minAmount != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("amountCents"), minAmount));
            }
            if (maxAmount != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("amountCents"), maxAmount));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
