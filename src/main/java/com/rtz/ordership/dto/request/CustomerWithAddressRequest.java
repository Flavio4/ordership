package com.rtz.ordership.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CustomerWithAddressRequest(
        @Valid @NotNull(message = "Los datos del cliente son obligatorios") CustomerRequest customer,

        @Valid @NotNull(message = "Los datos de la dirección son obligatorios") CustomerAddressRequest address) {
}
