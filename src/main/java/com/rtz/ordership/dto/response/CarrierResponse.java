package com.rtz.ordership.dto.response;

import com.rtz.ordership.entity.Carrier;
import com.rtz.ordership.entity.enums.CarrierType;

import java.time.Instant;
import java.util.UUID;

public record CarrierResponse(
        UUID id,
        String name,
        CarrierType type,
        String phone,
        UUID userId,
        Boolean active,
        Instant createdAt) {

    public static CarrierResponse fromEntity(Carrier carrier) {
        return new CarrierResponse(
                carrier.getId(),
                carrier.getName(),
                carrier.getType(),
                carrier.getPhone(),
                carrier.getUser() != null ? carrier.getUser().getId() : null,
                carrier.getActive(),
                carrier.getCreatedAt());
    }
}
