package com.rtz.ordership.service;

import com.rtz.ordership.dto.request.CustomerRequest;
import com.rtz.ordership.dto.request.CustomerUpdateRequest;
import com.rtz.ordership.dto.response.CustomerDetailResponse;
import com.rtz.ordership.dto.response.CustomerResponse;
import com.rtz.ordership.entity.Customer;
import com.rtz.ordership.exception.DuplicateResourceException;
import com.rtz.ordership.exception.ResourceNotFoundException;
import com.rtz.ordership.repository.CustomerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public List<CustomerResponse> getAllCustomers() {
        log.info("Listando todos los clientes activos");
        List<CustomerResponse> customers = customerRepository.findByActiveTrue().stream()
                .map(CustomerResponse::fromEntity)
                .toList();
        log.info("Se encontraron {} clientes", customers.size());
        return customers;
    }

    public CustomerResponse getCustomerById(UUID id) {
        log.info("Buscando cliente por ID: {}", id);
        Customer customer = findCustomerOrThrow(id);
        return CustomerResponse.fromEntity(customer);
    }

    @Transactional(readOnly = true)
    public CustomerDetailResponse getCustomerDetail(UUID id) {
        log.info("Obteniendo detalle completo del cliente ID: {}", id);
        Customer customer = findCustomerOrThrow(id);
        return CustomerDetailResponse.fromEntity(customer);
    }

    @Transactional
    public CustomerResponse createCustomer(CustomerRequest request) {
        log.info("Creando cliente: {} ({})", request.fullName(), request.phone());

        if (customerRepository.existsByPhone(request.phone())) {
            log.warn("Teléfono duplicado al crear cliente: {}", request.phone());
            throw new DuplicateResourceException("Ya existe un cliente con el teléfono: " + request.phone());
        }

        Customer customer = Customer.builder()
                .fullName(request.fullName())
                .phone(request.phone())
                .email(request.email())
                .notes(request.notes())
                .active(true)
                .build();

        customer = customerRepository.save(customer);
        log.info("Cliente creado - id: {}, nombre: {}", customer.getId(), customer.getFullName());
        return CustomerResponse.fromEntity(customer);
    }

    @Transactional
    public CustomerResponse updateCustomer(UUID id, CustomerUpdateRequest request) {
        log.info("Actualizando cliente ID: {}", id);
        Customer customer = findCustomerOrThrow(id);

        if (request.phone() != null) {
            if (!request.phone().equals(customer.getPhone()) && customerRepository.existsByPhone(request.phone())) {
                log.warn("Teléfono duplicado al actualizar cliente: {}", request.phone());
                throw new DuplicateResourceException("Ya existe un cliente con el teléfono: " + request.phone());
            }
            customer.setPhone(request.phone());
        }

        if (request.fullName() != null)
            customer.setFullName(request.fullName());
        if (request.email() != null)
            customer.setEmail(request.email());
        if (request.notes() != null)
            customer.setNotes(request.notes());
        if (request.active() != null)
            customer.setActive(request.active());

        customer = customerRepository.save(customer);
        log.info("Cliente actualizado - id: {}, nombre: {}", customer.getId(), customer.getFullName());
        return CustomerResponse.fromEntity(customer);
    }

    @Transactional
    public void deactivateCustomer(UUID id) {
        log.info("Desactivando cliente ID: {}", id);
        Customer customer = findCustomerOrThrow(id);
        customer.setActive(false);
        customerRepository.save(customer);
        log.info("Cliente desactivado - id: {}, nombre: {}", customer.getId(), customer.getFullName());
    }

    protected Customer findCustomerOrThrow(UUID id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Cliente no encontrado con ID: {}", id);
                    return new ResourceNotFoundException("Cliente no encontrado con ID: " + id);
                });
    }
}
