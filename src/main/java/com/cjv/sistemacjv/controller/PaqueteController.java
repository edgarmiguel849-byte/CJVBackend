package com.cjv.sistemacjv.controller;

import com.cjv.sistemacjv.entity.Paquete;
import com.cjv.sistemacjv.service.PaqueteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/paquetes")
public class PaqueteController {

    private final PaqueteService paqueteService;

    public PaqueteController(PaqueteService paqueteService) {
        this.paqueteService = paqueteService;
    }

    @GetMapping
    public List<Paquete> listarPaquetes() {
        return paqueteService.listarPaquetes();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Paquete> buscarPaquetePorId(@PathVariable Integer id) {
        return paqueteService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Paquete guardarPaquete(@RequestBody Paquete paquete) {
        return paqueteService.guardarPaquete(paquete);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Paquete> actualizarPaquete(
            @PathVariable Integer id,
            @RequestBody Paquete datosPaquete
    ) {
        return paqueteService.actualizarPaquete(id, datosPaquete)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarPaquete(@PathVariable Integer id) {
        boolean eliminado = paqueteService.eliminarPaquete(id);

        if (eliminado) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.notFound().build();
    }
}