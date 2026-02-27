package com.gpp.concurrency.repository;

import com.gpp.concurrency.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // Standard read (for optimistic locking, JPA handles the version check
    // automatically)
    // Optional<Product> findById(Long id); // Inherited from JpaRepository

    // Pessimistic read & write lock
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithPessimisticLock(Long id);
}
