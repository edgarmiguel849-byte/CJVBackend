package com.cjv.sistemacjv.controller;

import com.cjv.sistemacjv.entity.Bitacora;
import com.cjv.sistemacjv.service.BitacoraService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bitacora")
@PreAuthorize("hasRole('JEFE')")
public class BitacoraController {

    private final BitacoraService bitacoraService;

    public BitacoraController(BitacoraService bitacoraService) {
        this.bitacoraService = bitacoraService;
    }

    @GetMapping
    public List<Bitacora> listarBitacora() {
        return bitacoraService.listarBitacora();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Bitacora> buscarBitacoraPorId(@PathVariable Integer id) {
        return bitacoraService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Bitacora guardarBitacora(@RequestBody Bitacora bitacora) {
        return bitacoraService.guardarBitacora(bitacora);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Bitacora> actualizarBitacora(
            @PathVariable Integer id,
            @RequestBody Bitacora datosBitacora
    ) {
        return bitacoraService.actualizarBitacora(id, datosBitacora)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarBitacora(@PathVariable Integer id) {
        boolean eliminado = bitacoraService.eliminarBitacora(id);

        if (eliminado) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.notFound().build();
    }
}