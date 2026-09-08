package com.cjv.sistemacjv.controller;

import com.cjv.sistemacjv.entity.Contrato;
import com.cjv.sistemacjv.entity.ContratoArticulo;
import com.cjv.sistemacjv.service.ContratoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Lectura de los "Artículos Adquiridos" de un contrato.
 *
 * Va en un controlador aparte a propósito, igual que se hizo con la matriz
 * de adicionales: así no se le mete mano al ContratoController, que ya está
 * probado y funcionando.
 *
 * Es de PURA LECTURA. Los artículos se GUARDAN viajando dentro del propio
 * contrato (el campo 'articulos'), en las rutas que ya existen:
 * POST /api/contratos, PUT /api/contratos/{id} y el traspaso.
 */
@RestController
@RequestMapping("/api/contratos")
public class ContratoArticuloController {

    private final ContratoService contratoService;

    public ContratoArticuloController(ContratoService contratoService) {
        this.contratoService = contratoService;
    }

    /**
     * Los artículos de un contrato, para abrirlo a editar.
     *
     * GET /api/contratos/{id}/articulos
     * Respuesta: [ {"idContratoArticulo":1,"descripcion":"Anillo","monto":1500.00,"orden":0} ]
     *
     * Si el contrato es viejo y no tiene artículos capturados, devuelve UN
     * renglón inventado que dice "Contrato" con el monto que ya tenía. Ese
     * renglón no está guardado en la base: es la red de seguridad para que
     * al editarlo no se le borre el total.
     */
    @GetMapping("/{id}/articulos")
    public ResponseEntity<List<ContratoArticulo>> listarArticulos(@PathVariable Integer id) {
        Optional<Contrato> contrato = contratoService.buscarParaEditar(id);

        return contrato
                .map(c -> ResponseEntity.ok(c.getArticulos()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}