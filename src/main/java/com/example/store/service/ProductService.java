package com.example.store.service;

import com.example.store.entity.Product;
import com.example.store.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Page<Product> getProducts(Pageable pageable) {
        Page<Product> page = productRepository.findAll(pageable);
        List<Long> ids = page.getContent().stream().map(Product::getId).toList();
        List<Product> withOrders = productRepository.findAllWithOrdersByIdIn(ids);
        return PageSupport.reorder(page, Product::getId, withOrders);
    }

    @Transactional(readOnly = true)
    public Product getProductById(Long id) {
        return productRepository
                .findByIdWithOrders(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional
    public Product createProduct(String description) {
        Product product = new Product();
        product.setDescription(description);
        return productRepository.save(product);
    }
}
