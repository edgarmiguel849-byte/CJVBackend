package com.cjv.sistemacjv.controller;

import com.cjv.sistemacjv.dto.MatrizAdicionalesDTO;
import com.cjv.sistemacjv.service.MatrizAdicionalesService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Endpoints de la matriz de adicionales "0/AA".
 *
 * Va en un controlador aparte a propósito, para no meterle mano al
 * ContratoController que ya está probado y funcionando. Las rutas cuelgan
 * del mismo pasillo (/api/contratos/adicionales) para que todo combine.
 *
 * Los dos son de PURA LECTURA.
 */
@RestController
@RequestMapping("/api/contratos/adicionales")
public class MatrizAdicionalesController {

    private final MatrizAdicionalesService matrizAdicionalesService;

    public MatrizAdicionalesController(MatrizAdicionalesService matrizAdicionalesService) {
        this.matrizAdicionalesService = matrizAdicionalesService;
    }

    /**
     * Los años que tienen adicionales, para pintar los renglones fijos
     * "O.T. 0/26", "O.T. 0/25"... arriba de la lista de Órdenes de Trabajo.
     *
     * GET /api/contratos/adicionales/anios
     * Respuesta: [ {"anio":2026,"etiqueta":"0/26"}, {"anio":2025,"etiqueta":"0/25"} ]
     */
    @GetMapping("/anios")
    public List<MatrizAdicionalesService.AnioAdicionales> listarAnios() {
        return matrizAdicionalesService.listarAnios();
    }

    /**
     * La matriz de un año. Si no mandan año, se asume el actual.
     *
     * GET /api/contratos/adicionales/matriz?anio=2026
     */
    @GetMapping("/matriz")
    public MatrizAdicionalesDTO obtenerMatriz(
            @RequestParam(required = false) Integer anio) {

        int anioFinal = (anio != null) ? anio : LocalDate.now().getYear();
        return matrizAdicionalesService.obtenerMatriz(anioFinal);
    }
}