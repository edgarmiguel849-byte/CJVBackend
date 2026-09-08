package com.cjv.sistemacjv.controller;

import com.cjv.sistemacjv.dto.ReporteComisionesDTO;
import com.cjv.sistemacjv.service.ComisionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/comisiones")
@CrossOrigin(origins = "*")
public class ComisionController {

    @Autowired
    private ComisionService comisionService;

    /**
     * Reporte de un rango de fechas para la pantalla de Reportes.
     * Ejemplo: GET /api/comisiones?desde=2025-10-01&hasta=2026-06-30
     *
     * SOLO JEFE: aquí van las comisiones de todas las vendedoras y el
     * desglose completo de la caja. El mostrador no lo necesita para
     * trabajar y no debe ver cuánto gana cada quien.
     *
     * El cierre de caja NO pasa por aquí: CorteDiaService llama al
     * servicio directamente en Java, así que el mostrador sigue
     * pudiendo generar su corte sin problema.
     */
    @GetMapping
    @PreAuthorize("hasRole('JEFE')")
    public ResponseEntity<?> generar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        try {
            ReporteComisionesDTO reporte = comisionService.generar(desde, hasta);
            return ResponseEntity.ok(reporte);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}