package com.example.store.repository;

import com.example.store.entity.Product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.orders WHERE p.id = :id")
    Optional<Product> findByIdWithOrders(@Param("id") Long id);

    // See CustomerRepository.findAllWithOrdersByIdIn: callers paginate plain ids via
    // findAll(Pageable) first, then use this to fetch-join just that page's rows.
    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.orders WHERE p.id IN :ids")
    List<Product> findAllWithOrdersByIdIn(@Param("ids") List<Long> ids);
}
