package com.cjv.sistemacjv.controller;

import com.cjv.sistemacjv.dto.CorteDiaDTO;
import com.cjv.sistemacjv.service.CorteDiaService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Hoja de corte del día (la que replica el papel).
 * Ejemplo: /api/corte-dia?fecha=2026-08-06
 *
 * Sin @PreAuthorize: igual que el reporte de comisiones, el mostrador
 * también necesita ver la hoja completa.
 */
@RestController
@RequestMapping("/api/corte-dia")
public class CorteDiaController {

    private final CorteDiaService corteDiaService;

    public CorteDiaController(CorteDiaService corteDiaService) {
        this.corteDiaService = corteDiaService;
    }

    @GetMapping
    public CorteDiaDTO obtenerCorte(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha
    ) {
        return corteDiaService.generar(fecha);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> manejarValidacion(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}