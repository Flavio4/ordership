package com.rtz.ordership.dto.response;

import com.rtz.ordership.entity.CustomerAddress;

import java.time.Instant;
import java.util.UUID;

public record CustomerAddressResponse(
        UUID id,
        String label,
        String street,
        String city,
        String description,
        Double latitude,
        Double longitude,
        Boolean isDefault,
        ZoneResponse zone,
        Boolean active,
        Instant createdAt) {
    public static CustomerAddressResponse fromEntity(CustomerAddress address) {
        return new CustomerAddressResponse(
                address.getId(),
                address.getLabel(),
                address.getStreet(),
                address.getCity(),
                address.getDescription(),
                address.getLatitude(),
                address.getLongitude(),
                address.getIsDefault(),
                ZoneResponse.fromEntity(address.getZone()),
                address.getActive(),
                address.getCreatedAt());
    }
}
