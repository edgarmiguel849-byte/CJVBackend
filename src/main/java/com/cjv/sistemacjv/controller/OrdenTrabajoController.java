package com.cjv.sistemacjv.controller;

import com.cjv.sistemacjv.entity.OrdenTrabajo;
import com.cjv.sistemacjv.service.OrdenTrabajoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/ordenes-trabajo")
@CrossOrigin(origins = "http://localhost:4200")
public class OrdenTrabajoController {

    @Autowired
    private OrdenTrabajoService ordenTrabajoService;

    // Listado (solo activas). Si viene ?carrera=xxx, filtra por nombre de carrera.
    @GetMapping
    public List<OrdenTrabajo> listar(@RequestParam(required = false) String carrera) {
        if (carrera != null && !carrera.trim().isEmpty()) {
            return ordenTrabajoService.buscarPorCarrera(carrera.trim());
        }
        return ordenTrabajoService.listar();
    }

    // Búsqueda exacta por número y año (ej. 44/2026)
    @GetMapping("/buscar")
    public ResponseEntity<OrdenTrabajo> buscarPorNumeroAnio(
            @RequestParam Integer numero,
            @RequestParam Integer anio) {
        Optional<OrdenTrabajo> ot = ordenTrabajoService.buscarPorNumeroAnio(numero, anio);
        return ot.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Obtener por id
    @GetMapping("/{id}")
    public ResponseEntity<OrdenTrabajo> obtener(@PathVariable Integer id) {
        return ordenTrabajoService.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Crear
    @PostMapping
    public ResponseEntity<?> crear(@RequestBody OrdenTrabajo ot) {
        try {
            OrdenTrabajo creada = ordenTrabajoService.crear(ot);
            return ResponseEntity.ok(creada);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Editar — Mostrador NO puede; solo Jefe y Administrador.
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('JEFE', 'ADMINISTRADOR')")
    public ResponseEntity<?> editar(@PathVariable Integer id, @RequestBody OrdenTrabajo ot) {
        try {
            OrdenTrabajo actualizada = ordenTrabajoService.editar(id, ot);
            return ResponseEntity.ok(actualizada);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Eliminar (lógico) — solo Jefe
    // Eliminar (lógico) — Jefe y Administrador.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('JEFE', 'ADMINISTRADOR')")
    public ResponseEntity<?> eliminar(@PathVariable Integer id) {
        try {
            ordenTrabajoService.eliminar(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


    // Matriz de la O.T. (la hoja tipo formato impreso)
    @GetMapping("/{id}/matriz")
    public ResponseEntity<?> obtenerMatriz(@PathVariable Integer id) {
        try {
            return ResponseEntity.ok(ordenTrabajoService.generarMatriz(id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}