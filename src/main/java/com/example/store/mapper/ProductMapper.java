package com.example.store.mapper;

import com.example.store.dto.ProductDTO;
import com.example.store.entity.Order;
import com.example.store.entity.Product;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    @Mapping(target = "orders", source = "orders", qualifiedByName = "ordersToOrderIds")
    ProductDTO productToProductDTO(Product product);

    List<ProductDTO> productsToProductDTOs(List<Product> products);

    @Named("ordersToOrderIds")
    default List<Long> ordersToOrderIds(List<Order> orders) {
        return orders.stream().map(Order::getId).toList();
    }
}
