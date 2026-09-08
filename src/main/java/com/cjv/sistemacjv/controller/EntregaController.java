package com.cjv.sistemacjv.controller;

import com.cjv.sistemacjv.dto.RenglonEntregaDTO;
import com.cjv.sistemacjv.entity.Entrega;
import com.cjv.sistemacjv.service.EntregaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/entregas")
public class EntregaController {

    private final EntregaService entregaService;

    public EntregaController(EntregaService entregaService) {
        this.entregaService = entregaService;
    }

    @GetMapping
    public List<Entrega> listarEntregas() {
        return entregaService.listarEntregas();
    }

    /**
     * Entregas de una O.T., en el orden de la lista del papel.
     * Ejemplo: /api/entregas/por-orden-trabajo?idOrdenTrabajo=3
     */
    @GetMapping("/por-orden-trabajo")
    public List<Entrega> listarPorOrdenTrabajo(@RequestParam Integer idOrdenTrabajo) {
        return entregaService.listarPorOrdenTrabajo(idOrdenTrabajo);
    }

    /**
     * La lista de una O.T. para la pantalla de Entregas: cada alumno con
     * lo que debe y si ya se le entregó.
     * Ejemplo: /api/entregas/renglones?idOrdenTrabajo=3
     */
    @GetMapping("/renglones")
    public List<RenglonEntregaDTO> listarRenglones(@RequestParam Integer idOrdenTrabajo) {
        return entregaService.listarRenglonesDeOrdenTrabajo(idOrdenTrabajo);
    }

    /**
     * Las entregas que se hicieron con saldo pendiente, con la nota que
     * dejó quien entregó. Es la vista de revisión del jefe.
     */
    @GetMapping("/con-adeudo")
    public List<Entrega> listarConAdeudo() {
        return entregaService.listarConAdeudo();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Entrega> buscarEntregaPorId(@PathVariable Integer id) {
        return entregaService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Registra la entrega de los cuadros. El servidor calcula el saldo;
     * el navegador solo manda el contrato y el comentario.
     */
    @PostMapping("/entregar")
    public Entrega entregar(@RequestBody EntregaRequest peticion) {
        return entregaService.entregar(
                peticion.getIdContrato(),
                peticion.getComentarios()
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('JEFE')")
    public ResponseEntity<Void> eliminarEntrega(@PathVariable Integer id) {
        boolean eliminado = entregaService.eliminarEntrega(id);

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
     * Lo único que el navegador tiene permitido mandar: qué contrato y por
     * qué. El saldo y la fecha los pone el servidor.
     */
    public static class EntregaRequest {

        private Integer idContrato;
        private String comentarios;

        public Integer getIdContrato() {
            return idContrato;
        }

        public void setIdContrato(Integer idContrato) {
            this.idContrato = idContrato;
        }

        public String getComentarios() {
            return comentarios;
        }

        public void setComentarios(String comentarios) {
            this.comentarios = comentarios;
        }
    }
}