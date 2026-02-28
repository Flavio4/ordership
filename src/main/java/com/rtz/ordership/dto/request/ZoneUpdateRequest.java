package com.rtz.ordership.dto.request;

public record ZoneUpdateRequest(
        String name,
        String description,
        Boolean active) {
}
