package com.cjv.sistemacjv.dto;

import com.cjv.sistemacjv.entity.OrdenTrabajo;

import java.math.BigDecimal;
import java.util.List;

/**
 * La matriz completa de una O.T. (la hoja impresa).
 * - ordenTrabajo: el encabezado (escuela, carrera, vendedora, fechas...)
 * - meses: las columnas dinámicas, en orden ("Octubre 2025", "Noviembre 2025"...)
 * - filas: un alumno por fila
 * - totalAnticipos y totalesPorMes: la fila de totales del pie
 * - totalGeneral / abonadoGeneral / restaGeneral: los grandes totales
 */
public class MatrizOrdenTrabajoDTO {

    private OrdenTrabajo ordenTrabajo;
    private List<String> meses;
    private List<FilaMatrizDTO> filas;

    private BigDecimal totalAnticipos;
    private List<BigDecimal> totalesPorMes;   // alineado con 'meses'

    private BigDecimal totalGeneral;
    private BigDecimal abonadoGeneral;
    private BigDecimal restaGeneral;

    public MatrizOrdenTrabajoDTO(OrdenTrabajo ordenTrabajo, List<String> meses,
                                 List<FilaMatrizDTO> filas, BigDecimal totalAnticipos,
                                 List<BigDecimal> totalesPorMes, BigDecimal totalGeneral,
                                 BigDecimal abonadoGeneral, BigDecimal restaGeneral) {
        this.ordenTrabajo = ordenTrabajo;
        this.meses = meses;
        this.filas = filas;
        this.totalAnticipos = totalAnticipos;
        this.totalesPorMes = totalesPorMes;
        this.totalGeneral = totalGeneral;
        this.abonadoGeneral = abonadoGeneral;
        this.restaGeneral = restaGeneral;
    }

    public OrdenTrabajo getOrdenTrabajo() { return ordenTrabajo; }
    public List<String> getMeses() { return meses; }
    public List<FilaMatrizDTO> getFilas() { return filas; }
    public BigDecimal getTotalAnticipos() { return totalAnticipos; }
    public List<BigDecimal> getTotalesPorMes() { return totalesPorMes; }
    public BigDecimal getTotalGeneral() { return totalGeneral; }
    public BigDecimal getAbonadoGeneral() { return abonadoGeneral; }
    public BigDecimal getRestaGeneral() { return restaGeneral; }
}