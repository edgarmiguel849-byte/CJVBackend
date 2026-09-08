package com.cjv.sistemacjv.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * La matriz completa de adicionales de un año (la hoja "0/26").
 *
 * OJO: aquí NO hay una O.T. de verdad. Los adicionales siguen siendo
 * contratos sin orden de trabajo; esto es solo una consulta por año que
 * el frontend pinta como si fuera una O.T. más en la lista.
 *
 * - etiqueta: "0/26", ya armada, para no cortar años en el frontend
 * - columnasPago: cuántas columnas FOLIO+PAGO lleva la hoja (mínimo 7,
 *   crece si algún alumno tiene más pagos)
 * - totalesPorColumna: la fila de sumas del pie, alineada con esas columnas
 */
public class MatrizAdicionalesDTO {

    private Integer anio;
    private String etiqueta;
    private Integer columnasPago;
    private List<FilaAdicionalDTO> filas;

    /**
     * Suma de la columna RECIBOS ANT.: todo el dinero que viene arrastrado
     * de contratos anteriores por recontratación.
     *
     * Va aparte de totalAnticipos a propósito. Antes las dos cantidades
     * venían sumadas en una sola casilla y no se sabía cuánto era de cada
     * cosa. Este dinero NO entró a la caja el día del contrato nuevo.
     */
    private BigDecimal totalRecibosAnteriores;

    /** Suma de la columna ANTICIPO: solo lo que sí entró a la caja ese día. */
    private BigDecimal totalAnticipos;

    private List<BigDecimal> totalesPorColumna;   // alineado con columnasPago

    private BigDecimal totalGeneral;
    private BigDecimal abonadoGeneral;
    private BigDecimal restaGeneral;

    private Integer cantidadContratos;
    private Integer cantidadCancelados;

    public MatrizAdicionalesDTO(Integer anio, String etiqueta, Integer columnasPago,
                                List<FilaAdicionalDTO> filas,
                                BigDecimal totalRecibosAnteriores,
                                BigDecimal totalAnticipos,
                                List<BigDecimal> totalesPorColumna, BigDecimal totalGeneral,
                                BigDecimal abonadoGeneral, BigDecimal restaGeneral,
                                Integer cantidadContratos, Integer cantidadCancelados) {
        this.anio = anio;
        this.etiqueta = etiqueta;
        this.columnasPago = columnasPago;
        this.filas = filas;
        this.totalRecibosAnteriores = totalRecibosAnteriores;
        this.totalAnticipos = totalAnticipos;
        this.totalesPorColumna = totalesPorColumna;
        this.totalGeneral = totalGeneral;
        this.abonadoGeneral = abonadoGeneral;
        this.restaGeneral = restaGeneral;
        this.cantidadContratos = cantidadContratos;
        this.cantidadCancelados = cantidadCancelados;
    }

    public Integer getAnio() { return anio; }
    public String getEtiqueta() { return etiqueta; }
    public Integer getColumnasPago() { return columnasPago; }
    public List<FilaAdicionalDTO> getFilas() { return filas; }
    public BigDecimal getTotalRecibosAnteriores() { return totalRecibosAnteriores; }
    public BigDecimal getTotalAnticipos() { return totalAnticipos; }
    public List<BigDecimal> getTotalesPorColumna() { return totalesPorColumna; }
    public BigDecimal getTotalGeneral() { return totalGeneral; }
    public BigDecimal getAbonadoGeneral() { return abonadoGeneral; }
    public BigDecimal getRestaGeneral() { return restaGeneral; }
    public Integer getCantidadContratos() { return cantidadContratos; }
    public Integer getCantidadCancelados() { return cantidadCancelados; }
}