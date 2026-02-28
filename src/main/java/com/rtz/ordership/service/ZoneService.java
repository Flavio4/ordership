package com.rtz.ordership.service;

import com.rtz.ordership.dto.request.ZoneRequest;
import com.rtz.ordership.dto.request.ZoneUpdateRequest;
import com.rtz.ordership.dto.response.ZoneResponse;
import com.rtz.ordership.entity.Zone;
import com.rtz.ordership.exception.DuplicateResourceException;
import com.rtz.ordership.exception.ResourceNotFoundException;
import com.rtz.ordership.repository.ZoneRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class ZoneService {

    private final ZoneRepository zoneRepository;

    public ZoneService(ZoneRepository zoneRepository) {
        this.zoneRepository = zoneRepository;
    }

    public List<ZoneResponse> getAllZones() {
        log.info("Listando todas las zonas activas");
        List<ZoneResponse> zones = zoneRepository.findByActiveTrue().stream()
                .map(ZoneResponse::fromEntity)
                .toList();
        log.info("Se encontraron {} zonas activas", zones.size());
        return zones;
    }

    public ZoneResponse getZoneById(UUID id) {
        log.info("Buscando zona por ID: {}", id);
        Zone zone = findZoneOrThrow(id);
        return ZoneResponse.fromEntity(zone);
    }

    @Transactional
    public ZoneResponse createZone(ZoneRequest request) {
        log.info("Creando zona: {}", request.name());

        if (zoneRepository.existsByName(request.name())) {
            log.warn("Zona duplicada: {}", request.name());
            throw new DuplicateResourceException("Ya existe una zona con el nombre: " + request.name());
        }

        Zone zone = Zone.builder()
                .name(request.name())
                .description(request.description())
                .active(true)
                .build();

        zone = zoneRepository.save(zone);
        log.info("Zona creada - id: {}, nombre: {}", zone.getId(), zone.getName());
        return ZoneResponse.fromEntity(zone);
    }

    @Transactional
    public ZoneResponse updateZone(UUID id, ZoneUpdateRequest request) {
        log.info("Actualizando zona ID: {}", id);
        Zone zone = findZoneOrThrow(id);

        if (request.name() != null) {
            if (!request.name().equals(zone.getName()) && zoneRepository.existsByName(request.name())) {
                log.warn("Zona duplicada al actualizar: {}", request.name());
                throw new DuplicateResourceException("Ya existe una zona con el nombre: " + request.name());
            }
            zone.setName(request.name());
        }
        if (request.description() != null)
            zone.setDescription(request.description());
        if (request.active() != null)
            zone.setActive(request.active());

        zone = zoneRepository.save(zone);
        log.info("Zona actualizada - id: {}, nombre: {}", zone.getId(), zone.getName());
        return ZoneResponse.fromEntity(zone);
    }

    @Transactional
    public void deactivateZone(UUID id) {
        log.info("Desactivando zona ID: {}", id);
        Zone zone = findZoneOrThrow(id);
        zone.setActive(false);
        zoneRepository.save(zone);
        log.info("Zona desactivada - id: {}, nombre: {}", zone.getId(), zone.getName());
    }

    private Zone findZoneOrThrow(UUID id) {
        return zoneRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Zona no encontrada con ID: {}", id);
                    return new ResourceNotFoundException("Zona no encontrada con ID: " + id);
                });
    }
}
