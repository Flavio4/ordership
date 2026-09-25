package com.rtz.ordership.service;

import com.rtz.ordership.dto.request.CustomerRequest;
import com.rtz.ordership.dto.request.CustomerUpdateRequest;
import com.rtz.ordership.dto.request.CustomerWithAddressRequest;
import com.rtz.ordership.dto.response.CustomerDetailResponse;
import com.rtz.ordership.dto.response.CustomerResponse;
import com.rtz.ordership.entity.Customer;
import com.rtz.ordership.exception.DuplicateResourceException;
import com.rtz.ordership.exception.ResourceNotFoundException;
import com.rtz.ordership.repository.CustomerRepository;
import com.rtz.ordership.util.PhoneNumbers;
import com.rtz.ordership.util.SearchPatterns;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerAddressService addressService;

    public CustomerService(CustomerRepository customerRepository, CustomerAddressService addressService) {
        this.customerRepository = customerRepository;
        this.addressService = addressService;
    }

    @Transactional(readOnly = true)
    public Page<CustomerDetailResponse> getAllCustomers(String query, Pageable pageable) {
        log.info("Listando clientes activos paginados | query: {}", query);

        Page<CustomerDetailResponse> customersPage = customerRepository
                .searchActive(SearchPatterns.containsLike(query), SearchPatterns.phoneContainsLike(query), pageable)
                .map(CustomerDetailResponse::fromEntity);

        log.info("Se encontraron {} clientes en la página actual (Total: {})",
                customersPage.getNumberOfElements(), customersPage.getTotalElements());
        return customersPage;
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
        String phone = PhoneNumbers.normalize(request.phone());
        log.info("Creando cliente: {} ({})", request.fullName(), phone);

        if (customerRepository.existsByPhone(phone)) {
            log.warn("Teléfono duplicado al crear cliente: {}", phone);
            throw new DuplicateResourceException("Ya existe un cliente con el teléfono: " + phone);
        }

        Customer customer = Customer.builder()
                .fullName(request.fullName())
                .phone(phone)
                .email(request.email())
                .notes(request.notes())
                .active(true)
                .build();

        customer = customerRepository.save(customer);
        log.info("Cliente creado - id: {}, nombre: {}", customer.getId(), customer.getFullName());
        return CustomerResponse.fromEntity(customer);
    }

    @Transactional
    public CustomerResponse createCustomerWithAddress(CustomerWithAddressRequest request) {
        log.info("Creando cliente con dirección inicial: {} ({})", request.customer().fullName(),
                request.customer().phone());

        // 1. Crear el cliente
        CustomerResponse customer = createCustomer(request.customer());

        // 2. Crear su dirección asociada
        addressService.addAddress(customer.id(), request.address());

        return customer;
    }

    @Transactional
    public CustomerResponse updateCustomer(UUID id, CustomerUpdateRequest request) {
        log.info("Actualizando cliente ID: {}", id);
        Customer customer = findCustomerOrThrow(id);

        if (request.phone() != null) {
            String phone = PhoneNumbers.normalize(request.phone());
            if (!phone.equals(customer.getPhone()) && customerRepository.existsByPhone(phone)) {
                log.warn("Teléfono duplicado al actualizar cliente: {}", phone);
                throw new DuplicateResourceException("Ya existe un cliente con el teléfono: " + phone);
            }
            customer.setPhone(phone);
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
