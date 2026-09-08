package com.cjv.sistemacjv.controller;

import com.cjv.sistemacjv.entity.Carrera;
import com.cjv.sistemacjv.service.CarreraService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/carreras")
public class CarreraController {

    private final CarreraService carreraService;

    public CarreraController(CarreraService carreraService) {
        this.carreraService = carreraService;
    }

    @GetMapping
    public List<Carrera> listarCarreras() {
        return carreraService.listarCarreras();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Carrera> buscarCarreraPorId(@PathVariable Integer id) {
        return carreraService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Carrera guardarCarrera(@RequestBody Carrera carrera) {
        return carreraService.guardarCarrera(carrera);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Carrera> actualizarCarrera(
            @PathVariable Integer id,
            @RequestBody Carrera datosCarrera
    ) {
        return carreraService.actualizarCarrera(id, datosCarrera)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarCarrera(@PathVariable Integer id) {
        boolean eliminado = carreraService.eliminarCarrera(id);

        if (eliminado) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.notFound().build();
    }
}