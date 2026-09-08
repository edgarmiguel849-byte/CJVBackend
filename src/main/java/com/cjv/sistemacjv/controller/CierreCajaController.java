package com.cjv.sistemacjv.controller;

import com.cjv.sistemacjv.entity.CierreCaja;
import com.cjv.sistemacjv.service.CierreCajaService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/cierres-caja")
public class CierreCajaController {

    private final CierreCajaService cierreCajaService;

    public CierreCajaController(CierreCajaService cierreCajaService) {
        this.cierreCajaService = cierreCajaService;
    }

    @GetMapping
    public List<CierreCaja> listarCierresCaja() {
        return cierreCajaService.listarCierresCaja();
    }

    /** Historial del más reciente al más viejo. */
    @GetMapping("/historial")
    public List<CierreCaja> listarHistorial() {
        return cierreCajaService.listarHistorial();
    }

    /**
     * Busca el cierre de un día. Si ese día todavía no se cierra,
     * devuelve 204 (sin contenido), que el frontend lee como "aún no hay".
     * Ejemplo: /api/cierres-caja/por-fecha?fecha=2026-08-07
     */
    @GetMapping("/por-fecha")
    public ResponseEntity<CierreCaja> buscarPorFecha(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha
    ) {
        return cierreCajaService.buscarPorFecha(fecha)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CierreCaja> buscarCierreCajaPorId(@PathVariable Integer id) {
        return cierreCajaService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * El mostrador envía el cierre del día con el efectivo que contó.
     * El servidor recalcula lo esperado; el navegador solo manda lo contado.
     */
    @PostMapping("/enviar")
    public CierreCaja enviar(@RequestBody EnvioCierreRequest peticion) {
        return cierreCajaService.enviar(
                peticion.getFecha(),
                peticion.getEfectivoContado(),
                peticion.getComentarios()
        );
    }

    /** Solo el Jefe. */
    @PutMapping("/{id}/autorizar")
    @PreAuthorize("hasRole('JEFE')")
    public CierreCaja autorizar(@PathVariable Integer id) {
        return cierreCajaService.autorizar(id);
    }

    /** Solo el Jefe. Devuelve un cierre autorizado a estado ENVIADO. */
    @PutMapping("/{id}/reabrir")
    @PreAuthorize("hasRole('JEFE')")
    public CierreCaja reabrir(@PathVariable Integer id) {
        return cierreCajaService.reabrir(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('JEFE')")
    public ResponseEntity<Void> eliminarCierreCaja(@PathVariable Integer id) {
        boolean eliminado = cierreCajaService.eliminarCierreCaja(id);

        if (eliminado) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.notFound().build();
    }

    /**
     * Red de seguridad: convierte errores de validación en 400 con mensaje
     * legible, en lugar de un 500 feo.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> manejarValidacion(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    /**
     * Lo único que el navegador tiene permitido mandar: la fecha, el efectivo
     * contado y el comentario. Todo lo demás lo calcula el servidor.
     */
    public static class EnvioCierreRequest {

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate fecha;

        private BigDecimal efectivoContado;
        private String comentarios;

        public LocalDate getFecha() {
            return fecha;
        }

        public void setFecha(LocalDate fecha) {
            this.fecha = fecha;
        }

        public BigDecimal getEfectivoContado() {
            return efectivoContado;
        }

        public void setEfectivoContado(BigDecimal efectivoContado) {
            this.efectivoContado = efectivoContado;
        }

        public String getComentarios() {
            return comentarios;
        }

        public void setComentarios(String comentarios) {
            this.comentarios = comentarios;
        }
    }
}