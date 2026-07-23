package com.example.store.controller;

import com.example.store.dto.CreateProductRequest;
import com.example.store.dto.ProductDTO;
import com.example.store.entity.Product;
import com.example.store.mapper.ProductMapper;
import com.example.store.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @GetMapping
    public PagedModel<ProductDTO> getAllProducts(@PageableDefault(sort = "id") Pageable pageable) {
        Page<Product> page = productRepository.findAll(pageable);
        List<Long> ids = page.getContent().stream().map(Product::getId).toList();
        List<Product> withOrders = productRepository.findAllWithOrdersByIdIn(ids);
        return PageSupport.toPagedModel(page, Product::getId, withOrders, productMapper::productToProductDTO);
    }

    @GetMapping("/{id}")
    public ProductDTO getProductById(@PathVariable Long id) {
        Product product = productRepository
                .findByIdWithOrders(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return productMapper.productToProductDTO(product);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDTO createProduct(@RequestBody CreateProductRequest request) {
        Product product = new Product();
        product.setDescription(request.getDescription());
        return productMapper.productToProductDTO(productRepository.save(product));
    }
}
