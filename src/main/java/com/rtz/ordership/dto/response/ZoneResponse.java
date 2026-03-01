package com.rtz.ordership.dto.response;

import com.rtz.ordership.entity.Zone;

import java.time.Instant;
import java.util.UUID;

public record ZoneResponse(
        UUID id,
        String name,
        String description,
        Boolean active,
        Instant createdAt) {
    public static ZoneResponse fromEntity(Zone zone) {
        if (zone == null)
            return null;
        return new ZoneResponse(
                zone.getId(),
                zone.getName(),
                zone.getDescription(),
                zone.getActive(),
                zone.getCreatedAt());
    }
}
