package com.rtz.ordership.service;

import com.rtz.ordership.dto.request.CustomerAddressRequest;
import com.rtz.ordership.dto.request.CustomerAddressUpdateRequest;
import com.rtz.ordership.dto.response.CustomerAddressResponse;
import com.rtz.ordership.entity.Customer;
import com.rtz.ordership.entity.CustomerAddress;
import com.rtz.ordership.entity.Zone;
import com.rtz.ordership.exception.ResourceNotFoundException;
import com.rtz.ordership.repository.CustomerAddressRepository;
import com.rtz.ordership.repository.ZoneRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class CustomerAddressService {

    private final CustomerAddressRepository addressRepository;
    private final CustomerService customerService;
    private final ZoneRepository zoneRepository;

    public CustomerAddressService(CustomerAddressRepository addressRepository,
            CustomerService customerService,
            ZoneRepository zoneRepository) {
        this.addressRepository = addressRepository;
        this.customerService = customerService;
        this.zoneRepository = zoneRepository;
    }

    public List<CustomerAddressResponse> getCustomerAddresses(UUID customerId) {
        log.info("Listando direcciones activas para el cliente ID: {}", customerId);
        // Validamos que el cliente exista
        customerService.findCustomerOrThrow(customerId);

        List<CustomerAddressResponse> addresses = addressRepository.findByCustomerIdAndActiveTrue(customerId).stream()
                .map(CustomerAddressResponse::fromEntity)
                .toList();
        log.info("Se encontraron {} direcciones para el cliente", addresses.size());
        return addresses;
    }

    @Transactional
    public CustomerAddressResponse addAddress(UUID customerId, CustomerAddressRequest request) {
        log.info("Agregando dirección al cliente ID: {} - Zona: {}", customerId, request.zoneId());

        Customer customer = customerService.findCustomerOrThrow(customerId);
        Zone zone = findZoneOrThrow(request.zoneId());

        CustomerAddress address = CustomerAddress.builder()
                .customer(customer)
                .zone(zone)
                .label(request.label())
                .street(request.street())
                .city(request.city())
                .description(request.description())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .mapUrl(request.mapUrl())
                .isDefault(request.isDefault() != null ? request.isDefault() : false)
                .active(true)
                .build();

        address = addressRepository.save(address);
        log.info("Dirección agregada - id: {}", address.getId());
        return CustomerAddressResponse.fromEntity(address);
    }

    @Transactional
    public CustomerAddressResponse updateAddress(UUID customerId, UUID addressId,
            CustomerAddressUpdateRequest request) {
        log.info("Actualizando dirección ID: {} del cliente ID: {}", addressId, customerId);

        CustomerAddress address = findAddressOrThrow(addressId);

        // Validar que la dirección realmente pertenezca al cliente especificado
        if (!address.getCustomer().getId().equals(customerId)) {
            log.warn("La dirección {} no pertenece al cliente {}", addressId, customerId);
            throw new ResourceNotFoundException("Dirección no encontrada para este cliente");
        }

        if (request.zoneId() != null) {
            Zone zone = findZoneOrThrow(request.zoneId());
            address.setZone(zone);
        }

        if (request.label() != null)
            address.setLabel(request.label());
        if (request.street() != null)
            address.setStreet(request.street());
        if (request.city() != null)
            address.setCity(request.city());
        if (request.description() != null)
            address.setDescription(request.description());
        if (request.latitude() != null)
            address.setLatitude(request.latitude());
        if (request.longitude() != null)
            address.setLongitude(request.longitude());
        if (request.mapUrl() != null)
            address.setMapUrl(request.mapUrl());
        if (request.isDefault() != null)
            address.setIsDefault(request.isDefault());
        if (request.active() != null)
            address.setActive(request.active());

        address = addressRepository.save(address);
        log.info("Dirección actualizada - id: {}", address.getId());
        return CustomerAddressResponse.fromEntity(address);
    }

    @Transactional
    public void deactivateAddress(UUID customerId, UUID addressId) {
        log.info("Desactivando dirección ID: {} del cliente ID: {}", addressId, customerId);

        CustomerAddress address = findAddressOrThrow(addressId);

        if (!address.getCustomer().getId().equals(customerId)) {
            log.warn("Intento de borrar dirección {} ajena al cliente {}", addressId, customerId);
            throw new ResourceNotFoundException("Dirección no encontrada para este cliente");
        }

        address.setActive(false);
        addressRepository.save(address);
        log.info("Dirección desactivada - id: {}", address.getId());
    }

    private CustomerAddress findAddressOrThrow(UUID id) {
        return addressRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Dirección no encontrada con ID: {}", id);
                    return new ResourceNotFoundException("Dirección no encontrada con ID: " + id);
                });
    }

    private Zone findZoneOrThrow(UUID id) {
        return zoneRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Zona no encontrada con ID: {}", id);
                    return new ResourceNotFoundException("Zona no encontrada con ID: " + id);
                });
    }
}
