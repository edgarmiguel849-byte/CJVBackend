package com.cjv.sistemacjv.controller;

import com.cjv.sistemacjv.dto.PaginaEgresosDTO;
import com.cjv.sistemacjv.entity.Egreso;
import com.cjv.sistemacjv.service.EgresoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/egresos")
public class EgresoController {

    private final EgresoService egresoService;

    public EgresoController(EgresoService egresoService) {
        this.egresoService = egresoService;
    }

    /**
     * Volcado completo, SIN filtro por rol. Por eso queda restringido:
     * la regla de "quién ve qué" vive en listarPaginado(), y este atajo
     * se la brincaba entero. Ninguna pantalla lo usa hoy.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('JEFE','ADMINISTRADOR')")
    public List<Egreso> listarEgresos() {
        return egresoService.listarEgresos();
    }

    /**
     * Le dice a Angular cómo arrancar la pantalla: BUSCAR_PRIMERO,
     * SOLO_HOY, CORTE_CERRADO o SIN_ACCESO. Mismos nombres que Contratos.
     * Es solo para pintar; los candados de verdad están en el servicio.
     */
    @GetMapping("/modo-pantalla")
    public EgresoService.ModoPantallaEgresos obtenerModoPantalla() {
        return egresoService.obtenerModoPantalla();
    }

    /**
     * Egresos de un rango de fechas, para el cierre de caja.
     * Ejemplo: /api/egresos/rango?desde=2026-08-06&hasta=2026-08-06
     */
    @GetMapping("/rango")
    @PreAuthorize("hasAnyRole('JEFE','ADMINISTRADOR')")
    public List<Egreso> listarPorRango(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta
    ) {
        return egresoService.listarPorRango(desde, hasta);
    }

    /**
     * Lista paginada para la pantalla de Egresos.
     * Ejemplo: /api/egresos/pagina?pagina=0&tamanio=20&texto=papeleria&desde=2026-08-01&hasta=2026-08-31
     * Todos los parámetros son opcionales.
     * Devuelve { pagina: {...}, totalMonto: 0.00 }
     */
    @GetMapping("/pagina")
    public PaginaEgresosDTO listarPaginado(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanio
    ) {
        return egresoService.listarPaginado(texto, desde, hasta, pagina, tamanio);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Egreso> buscarEgresoPorId(@PathVariable Integer id) {
        return egresoService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Egreso guardarEgreso(@RequestBody Egreso egreso) {
        return egresoService.guardarEgreso(egreso);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Egreso> actualizarEgreso(
            @PathVariable Integer id,
            @RequestBody Egreso datosEgreso
    ) {
        return egresoService.actualizarEgreso(id, datosEgreso)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('JEFE')")
    public ResponseEntity<Void> eliminarEgreso(@PathVariable Integer id) {
        boolean eliminado = egresoService.eliminarEgreso(id);

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
     * Los candados de EgresoService lanzan RuntimeException con un mensaje
     * escrito para que lo lea una persona ("el corte del 07/08 ya fue
     * entregado..."). Sin este manejador salían como 500 SIN cuerpo, y el
     * navegador pintaba su texto genérico: el usuario vería "no se pudo
     * guardar" sin enterarse de que el problema es el corte cerrado.
     *
     * Va DESPUÉS del de IllegalArgumentException a propósito: aquel es más
     * específico y Spring lo prefiere, así que sigue ganando el suyo.
     *
     * Costo asumido: un error de programación de verdad (un NullPointer,
     * por ejemplo) también cae aquí y sale como 400. Por eso, cuando no
     * hay mensaje, NO se inventa una causa: se manda un texto neutro que
     * manda a mirar la consola.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> manejarReglaDeNegocio(RuntimeException ex) {
        String mensaje = ex.getMessage();

        if (mensaje == null || mensaje.isBlank()) {
            mensaje = "No se pudo completar la operación. "
                    + "Revisa la consola del servidor para ver el detalle.";
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mensaje);
    }
}