package com.example.store.repository;

import com.example.store.entity.Customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    // A substring with no whitespace can only match within a single word of c.name,
    // never span two words - so a plain substring match doubles as a word match.
    @Query("SELECT c FROM Customer c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Customer> findByNameContaining(@Param("query") String query, Pageable pageable);

    // Collection fetch joins can't be combined with LIMIT/OFFSET (Hibernate falls back to
    // loading the whole table into memory to paginate). So callers paginate plain ids first
    // via findAll(Pageable)/findByNameContaining, then use this to fetch just that page's rows.
    @Query("SELECT DISTINCT c FROM Customer c LEFT JOIN FETCH c.orders WHERE c.id IN :ids")
    List<Customer> findAllWithOrdersByIdIn(@Param("ids") List<Long> ids);
}
