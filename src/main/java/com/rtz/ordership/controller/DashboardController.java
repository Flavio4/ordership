package com.rtz.ordership.controller;

import com.rtz.ordership.dto.response.DashboardResponse;
import com.rtz.ordership.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Estadísticas generales del negocio")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Obtener dashboard",
            description = "Resumen de los pedidos (Shopify y manuales) para la pantalla de inicio: ventas de hoy, de la semana y de "
                    + "los últimos 7 días, y lo que hay para hacer (pendientes, entregas de hoy y atrasadas, productos "
                    + "por completar, fallas de Shopify). \"Hoy\" es el día en Paraguay")
    public ResponseEntity<DashboardResponse> getDashboard() {
        return ResponseEntity.ok(dashboardService.getDashboard());
    }
}
