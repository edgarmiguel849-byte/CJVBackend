package com.cjv.sistemacjv.controller;

import com.cjv.sistemacjv.dto.ResultadoBusquedaAlumnoDTO;
import com.cjv.sistemacjv.dto.ContratoConSaldoDTO;
import com.cjv.sistemacjv.entity.Contrato;
import com.cjv.sistemacjv.service.ContratoService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/contratos")
public class ContratoController {

    private final ContratoService contratoService;

    public ContratoController(ContratoService contratoService) {
        this.contratoService = contratoService;
    }

    @GetMapping
    public List<Contrato> listarContratos() {
        return contratoService.listarContratos();
    }

    @GetMapping("/con-saldos")
    public List<ContratoConSaldoDTO> listarContratosConSaldos() {
        return contratoService.listarContratosConSaldos();
    }

    /**
     * Le dice a la pantalla de Contratos en qué modo debe arrancar,
     * según quién esté logueado. Angular llama a esto al entrar a la
     * pantalla y sabe qué mensaje pintar y qué filtros esconder.
     *
     * Ejemplo de respuesta para Mostrador con el corte del día abierto:
     *   { "modo": "SOLO_HOY", "rol": "ROLE_MOSTRADOR",
     *     "fechaHoy": "2026-08-14", "corteHoyTrabado": false }
     */
    @GetMapping("/modo-pantalla")
    public ContratoService.ModoPantallaContratos obtenerModoPantalla() {
        return contratoService.obtenerModoPantalla();
    }

    /**
     * Lista paginada con saldos para la pantalla de Contratos.
     * Ejemplo: /api/contratos/pagina?pagina=0&tamanio=20&texto=F6951&desde=2025-10-01&hasta=2026-06-30
     * Todos los parámetros son opcionales.
     *
     * OJO: aunque el frontend mande filtros, el servicio DECIDE qué se
     * puede ver según el rol. Mostrador siempre queda amarrado a hoy,
     * y Administrador/Jefe reciben una página vacía si no mandan filtro.
     */
    @GetMapping("/pagina")
    public Page<ContratoConSaldoDTO> listarPaginado(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanio
    ) {
        return contratoService.listarPaginadoConSaldos(texto, desde, hasta, pagina, tamanio);
    }

    /**
     * Igual que /pagina pero SOLO para adicionales (contratos sin O.T.).
     * Lo usa la pantalla de Contratos Adicionales.
     * Ejemplo: /api/contratos/adicionales/pagina?pagina=0&tamanio=20&texto=foto
     */
    @GetMapping("/adicionales/pagina")
    public Page<ContratoConSaldoDTO> listarPaginadoAdicionales(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanio
    ) {
        return contratoService.listarPaginadoAdicionales(texto, desde, hasta, pagina, tamanio);
    }

    /**
     * TRASPASO - Paso 1: busca el adicional anterior por folio para heredar sus
     * datos. Devuelve el contrato con su saldo, o un mensaje de error si no se
     * puede usar (inexistente, de grupo, o ya dado de baja).
     */
    @GetMapping("/adicionales/buscar-anterior")
    public ResponseEntity<?> buscarAnteriorParaTraspaso(@RequestParam String folio) {
        try {
            return ResponseEntity.ok(contratoService.buscarAnteriorParaTraspaso(folio));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * TRASPASO - Paso 2: crea el nuevo adicional y da de baja el anterior, en una
     * sola operación. Recibe el folio anterior por parámetro y el nuevo contrato
     * en el cuerpo.
     */
    @PostMapping("/adicionales/traspaso")
    public ResponseEntity<?> traspasarAdicional(@RequestParam String folioAnterior,
                                                @RequestBody Contrato nuevo) {
        try {
            return ResponseEntity.ok(contratoService.traspasarAdicional(nuevo, folioAnterior));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }



    @GetMapping("/{id}")
    public ResponseEntity<Contrato> buscarContratoPorId(@PathVariable Integer id) {
        return contratoService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> guardarContrato(@RequestBody Contrato contrato) {
        try {
            return ResponseEntity.ok(contratoService.guardarContrato(contrato));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarContrato(
            @PathVariable Integer id,
            @RequestBody Contrato datosContrato
    ) {
        try {
            return contratoService.actualizarContrato(id, datosContrato)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarContrato(@PathVariable Integer id) {
        boolean eliminado = contratoService.eliminarContrato(id);

        if (eliminado) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.notFound().build();
    }

    // Buscador de alumno: por nombre/apellidos o folio de contrato.
    // Devuelve cada contrato con su O.T., carrera y saldo.
    @GetMapping("/buscar-alumno")
    public List<ResultadoBusquedaAlumnoDTO> buscarAlumno(@RequestParam String texto) {
        return contratoService.buscarAlumno(texto);
    }
}