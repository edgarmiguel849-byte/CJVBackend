package com.cjv.sistemacjv.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un pago dentro de la matriz de adicionales: el par "FOLIO + PAGO"
 * que en el Excel ocupa dos columnas pegadas.
 *
 * Las devoluciones también viajan aquí: llegan con monto negativo
 * (así se guardan en la base) y su folio empieza con "Z". La bandera
 * esDevolucion sirve para que el frontend las pinte distinto.
 */
public class CeldaPagoDTO {

    private String folio;
    private BigDecimal monto;
    private LocalDate fecha;
    private boolean esDevolucion;

    public CeldaPagoDTO(String folio, BigDecimal monto,
                        LocalDate fecha, boolean esDevolucion) {
        this.folio = folio;
        this.monto = monto;
        this.fecha = fecha;
        this.esDevolucion = esDevolucion;
    }

    public String getFolio() { return folio; }
    public BigDecimal getMonto() { return monto; }
    public LocalDate getFecha() { return fecha; }
    public boolean isEsDevolucion() { return esDevolucion; }
}