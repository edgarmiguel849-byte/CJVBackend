package com.cjv.sistemacjv.controller;

import com.cjv.sistemacjv.entity.EstadoContrato;
import com.cjv.sistemacjv.service.EstadoContratoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/estados-contrato")
public class EstadoContratoController {

    private final EstadoContratoService estadoContratoService;

    public EstadoContratoController(EstadoContratoService estadoContratoService) {
        this.estadoContratoService = estadoContratoService;
    }

    @GetMapping
    public List<EstadoContrato> listarEstadosContrato() {
        return estadoContratoService.listarEstadosContrato();
    }

    @GetMapping("/{id}")
    public ResponseEntity<EstadoContrato> buscarEstadoContratoPorId(@PathVariable Integer id) {
        return estadoContratoService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public EstadoContrato guardarEstadoContrato(@RequestBody EstadoContrato estadoContrato) {
        return estadoContratoService.guardarEstadoContrato(estadoContrato);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EstadoContrato> actualizarEstadoContrato(
            @PathVariable Integer id,
            @RequestBody EstadoContrato datosEstadoContrato
    ) {
        return estadoContratoService.actualizarEstadoContrato(id, datosEstadoContrato)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarEstadoContrato(@PathVariable Integer id) {
        boolean eliminado = estadoContratoService.eliminarEstadoContrato(id);

        if (eliminado) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.notFound().build();
    }
}