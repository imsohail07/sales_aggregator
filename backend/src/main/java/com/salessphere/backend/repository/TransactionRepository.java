package com.salessphere.backend.repository;

import com.salessphere.backend.entity.Transaction;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.stream.Stream;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByTransactionCode(String transactionCode);

    boolean existsByTransactionCode(String transactionCode);

    Optional<Transaction> findFirstByOrderByCreatedAtDesc();

    // Using Stream for memory optimization when processing 100,000+ entries
    // join fetch is utilized to avoid N+1 Select issues
    @QueryHints(value = {
        @QueryHint(name = "org.hibernate.fetchSize", value = "500")
    })
    @Query("SELECT t FROM Transaction t JOIN FETCH t.region JOIN FETCH t.category")
    Stream<Transaction> streamAllTransactions();
}
