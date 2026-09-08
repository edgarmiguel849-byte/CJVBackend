package com.cjv.sistemacjv.controller;

import com.cjv.sistemacjv.entity.CierreCajaDetalle;
import com.cjv.sistemacjv.service.CierreCajaDetalleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cierres-caja-detalle")
@PreAuthorize("hasRole('JEFE')")
public class CierreCajaDetalleController {

    private final CierreCajaDetalleService cierreCajaDetalleService;

    public CierreCajaDetalleController(CierreCajaDetalleService cierreCajaDetalleService) {
        this.cierreCajaDetalleService = cierreCajaDetalleService;
    }

    @GetMapping
    public List<CierreCajaDetalle> listarDetalles() {
        return cierreCajaDetalleService.listarDetalles();
    }

    @GetMapping("/{id}")
    public ResponseEntity<CierreCajaDetalle> buscarDetallePorId(@PathVariable Integer id) {
        return cierreCajaDetalleService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public CierreCajaDetalle guardarDetalle(@RequestBody CierreCajaDetalle cierreCajaDetalle) {
        return cierreCajaDetalleService.guardarDetalle(cierreCajaDetalle);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CierreCajaDetalle> actualizarDetalle(
            @PathVariable Integer id,
            @RequestBody CierreCajaDetalle datosDetalle
    ) {
        return cierreCajaDetalleService.actualizarDetalle(id, datosDetalle)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarDetalle(@PathVariable Integer id) {
        boolean eliminado = cierreCajaDetalleService.eliminarDetalle(id);

        if (eliminado) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.notFound().build();
    }
}