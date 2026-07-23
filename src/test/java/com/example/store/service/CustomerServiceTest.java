package com.example.store.service;

import com.example.store.entity.Customer;
import com.example.store.repository.CustomerRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    @Test
    void createCustomerPersistsGivenName() {
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Customer created = customerService.createCustomer("Jane Doe");

        assertThat(created.getName()).isEqualTo("Jane Doe");
    }

    @Test
    void getCustomersReordersFetchedResultsToMatchThePagedIds() {
        Customer c1 = new Customer();
        c1.setId(1L);
        c1.setName("First");
        Customer c2 = new Customer();
        c2.setId(2L);
        c2.setName("Second");

        // Plain id page comes back in id order...
        Page<Customer> idPage = new PageImpl<>(List.of(c1, c2), PageRequest.of(0, 20), 2);
        when(customerRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(idPage);
        // ...but the fetch-joined lookup returns them in a different order.
        when(customerRepository.findAllWithOrdersByIdIn(List.of(1L, 2L))).thenReturn(List.of(c2, c1));

        Page<Customer> result = customerService.getCustomers(null, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(Customer::getId).containsExactly(1L, 2L);
    }
}
