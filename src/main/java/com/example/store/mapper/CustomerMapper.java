package com.example.store.mapper;

import com.example.store.dto.CustomerDTO;
import com.example.store.entity.Customer;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CustomerMapper {
    CustomerDTO customerToCustomerDTO(Customer customer);
}
