package com.cjv.sistemacjv.controller;

import com.cjv.sistemacjv.entity.Escuela;
import com.cjv.sistemacjv.service.EscuelaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/escuelas")
public class EscuelaController {

    private final EscuelaService escuelaService;

    public EscuelaController(EscuelaService escuelaService) {
        this.escuelaService = escuelaService;
    }

    @GetMapping
    public List<Escuela> listarEscuelas() {
        return escuelaService.listarEscuelas();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Escuela> buscarEscuelaPorId(@PathVariable Integer id) {
        return escuelaService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Escuela guardarEscuela(@RequestBody Escuela escuela) {
        return escuelaService.guardarEscuela(escuela);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Escuela> actualizarEscuela(
            @PathVariable Integer id,
            @RequestBody Escuela datosEscuela
    ) {
        return escuelaService.actualizarEscuela(id, datosEscuela)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarEscuela(@PathVariable Integer id) {
        boolean eliminado = escuelaService.eliminarEscuela(id);

        if (eliminado) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.notFound().build();
    }
}