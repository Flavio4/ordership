package com.rtz.ordership.service;

import com.rtz.ordership.dto.request.CarrierRequest;
import com.rtz.ordership.dto.response.CarrierResponse;
import com.rtz.ordership.entity.Carrier;
import com.rtz.ordership.entity.User;
import com.rtz.ordership.entity.enums.CarrierType;
import com.rtz.ordership.entity.enums.Role;
import com.rtz.ordership.exception.DuplicateResourceException;
import com.rtz.ordership.exception.ResourceNotFoundException;
import com.rtz.ordership.repository.CarrierRepository;
import com.rtz.ordership.repository.UserRepository;
import com.rtz.ordership.util.PhoneNumbers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class CarrierService {

    private final CarrierRepository carrierRepository;
    private final UserRepository userRepository;

    public CarrierService(CarrierRepository carrierRepository, UserRepository userRepository) {
        this.carrierRepository = carrierRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<CarrierResponse> getCarriers(boolean includeInactive) {
        List<Carrier> carriers = includeInactive
                ? carrierRepository.findAllByOrderByTypeAscNameAsc()
                : carrierRepository.findByActiveTrueOrderByTypeAscNameAsc();
        return carriers.stream().map(CarrierResponse::fromEntity).toList();
    }

    @Transactional
    public CarrierResponse createCarrier(CarrierRequest request) {
        log.info("Creando repartidor: {} ({})", request.name(), request.type());
        Carrier carrier = Carrier.builder()
                .name(request.name().trim())
                .type(request.type())
                .phone(normalizePhone(request.phone()))
                .user(resolveUser(request, null))
                .active(request.active() == null || request.active())
                .build();
        carrier = carrierRepository.save(carrier);
        log.info("Repartidor creado - id: {}", carrier.getId());
        return CarrierResponse.fromEntity(carrier);
    }

    @Transactional
    public CarrierResponse updateCarrier(UUID id, CarrierRequest request) {
        log.info("Actualizando repartidor ID: {}", id);
        Carrier carrier = findCarrierOrThrow(id);
        carrier.setName(request.name().trim());
        carrier.setType(request.type());
        carrier.setPhone(normalizePhone(request.phone()));
        carrier.setUser(resolveUser(request, id));
        if (request.active() != null) {
            carrier.setActive(request.active());
        }
        return CarrierResponse.fromEntity(carrierRepository.save(carrier));
    }

    @Transactional
    public void deactivateCarrier(UUID id) {
        log.info("Desactivando repartidor ID: {}", id);
        Carrier carrier = findCarrierOrThrow(id);
        carrier.setActive(false);
        carrierRepository.save(carrier);
    }

    public Carrier findCarrierOrThrow(UUID id) {
        return carrierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Repartidor no encontrado con ID: " + id));
    }

    private User resolveUser(CarrierRequest request, UUID carrierId) {
        if (request.userId() == null) {
            return null;
        }
        if (request.type() != CarrierType.OWN) {
            throw new IllegalArgumentException("Solo un repartidor propio puede vincularse a un usuario");
        }
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + request.userId()));
        if (user.getRole() != Role.DELIVERY) {
            throw new IllegalArgumentException("El usuario '" + user.getFullName() + "' no tiene el rol DELIVERY");
        }
        boolean taken = carrierId == null
                ? carrierRepository.existsByUserId(user.getId())
                : carrierRepository.existsByUserIdAndIdNot(user.getId(), carrierId);
        if (taken) {
            throw new DuplicateResourceException("El usuario '" + user.getFullName() + "' ya está vinculado a otro repartidor");
        }
        return user;
    }

    private static String normalizePhone(String phone) {
        return phone == null || phone.isBlank() ? null : PhoneNumbers.normalize(phone);
    }
}
