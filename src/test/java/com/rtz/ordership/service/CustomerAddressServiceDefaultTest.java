package com.rtz.ordership.service;

import com.rtz.ordership.dto.request.CustomerAddressRequest;
import com.rtz.ordership.dto.request.CustomerAddressUpdateRequest;
import com.rtz.ordership.entity.Customer;
import com.rtz.ordership.entity.CustomerAddress;
import com.rtz.ordership.entity.Zone;
import com.rtz.ordership.repository.CustomerAddressRepository;
import com.rtz.ordership.repository.CustomerRepository;
import com.rtz.ordership.repository.ZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomerAddressServiceDefaultTest {

    private CustomerAddressRepository addressRepository;
    private CustomerAddressService service;
    private final Customer customer = Customer.builder().id(UUID.randomUUID()).fullName("Cliente").phone("+595981000999").build();
    private final Zone zone = Zone.builder().id(UUID.randomUUID()).name("Asunción").build();

    @BeforeEach
    void setUp() {
        addressRepository = mock(CustomerAddressRepository.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        ZoneRepository zoneRepository = mock(ZoneRepository.class);
        when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(zoneRepository.findById(zone.getId())).thenReturn(Optional.of(zone));
        when(addressRepository.save(any())).thenAnswer(inv -> {
            CustomerAddress address = inv.getArgument(0);
            if (address.getId() == null) {
                address.setId(UUID.randomUUID());
            }
            return address;
        });
        service = new CustomerAddressService(addressRepository, customerRepository, zoneRepository);
    }

    @Test
    void newDefaultAddressUnsetsTheOtherDefaults() {
        var response = service.addAddress(customer.getId(), request(true));

        verify(addressRepository).unsetOtherDefaults(customer.getId(), response.id());
    }

    @Test
    void newNonDefaultAddressKeepsTheCurrentDefault() {
        service.addAddress(customer.getId(), request(false));

        verify(addressRepository, never()).unsetOtherDefaults(any(), any());
    }

    @Test
    void markingAnExistingAddressAsDefaultUnsetsTheOthers() {
        CustomerAddress address = CustomerAddress.builder()
                .id(UUID.randomUUID()).customer(customer).zone(zone).isDefault(false).build();
        when(addressRepository.findById(address.getId())).thenReturn(Optional.of(address));

        service.updateAddress(customer.getId(), address.getId(),
                new CustomerAddressUpdateRequest(null, null, null, null, null, null, null, null, true, null));

        verify(addressRepository).unsetOtherDefaults(customer.getId(), address.getId());
    }

    private CustomerAddressRequest request(boolean isDefault) {
        return new CustomerAddressRequest(zone.getId(), "Casa", "Calle 1", "Asunción", null, null, null, null, isDefault);
    }
}
