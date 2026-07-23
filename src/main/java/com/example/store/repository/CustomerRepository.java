package com.example.store.repository;

import com.example.store.entity.Customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    @Query("SELECT DISTINCT c FROM Customer c LEFT JOIN FETCH c.orders")
    List<Customer> findAllWithOrders();

    // A substring with no whitespace can only match within a single word of c.name,
    // never span two words - so a plain substring match doubles as a word match.
    @Query(
            "SELECT DISTINCT c FROM Customer c LEFT JOIN FETCH c.orders "
                    + "WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Customer> findAllWithOrdersByNameContaining(@Param("query") String query);
}
