package com.example.store.repository;

import com.example.store.entity.Order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    @Query("SELECT o FROM Order o JOIN FETCH o.customer LEFT JOIN FETCH o.products WHERE o.id = :id")
    Optional<Order> findByIdWithCustomerAndProducts(@Param("id") Long id);

    // See CustomerRepository.findAllWithOrdersByIdIn: callers paginate plain ids via
    // findAll(Pageable) first, then use this to fetch-join just that page's rows.
    @Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.customer LEFT JOIN FETCH o.products WHERE o.id IN :ids")
    List<Order> findAllWithCustomerAndProductsByIdIn(@Param("ids") List<Long> ids);
}
