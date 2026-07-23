package com.example.store.entity;

import jakarta.persistence.*;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Entity
@Data
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq")
    @SequenceGenerator(name = "product_seq", sequenceName = "product_id_seq", allocationSize = 1)
    private Long id;

    private String description;

    // Excluded from toString/equals/hashCode: Order back-references this Product,
    // and Lombok's generated methods would recurse into the cycle otherwise.
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToMany(mappedBy = "products", fetch = FetchType.LAZY)
    private List<Order> orders = new ArrayList<>();
}
