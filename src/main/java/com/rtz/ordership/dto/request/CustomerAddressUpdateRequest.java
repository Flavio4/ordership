package com.rtz.ordership.dto.request;

import java.util.UUID;

public record CustomerAddressUpdateRequest(
                UUID zoneId,
                String label,
                String street,
                String city,
                String description,
                Double latitude,
                Double longitude,
                Boolean isDefault,
                Boolean active) {
}
