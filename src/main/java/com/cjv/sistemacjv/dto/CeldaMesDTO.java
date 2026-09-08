package com.cjv.sistemacjv.dto;

import java.math.BigDecimal;

/**
 * Un pago dentro de una celda de la matriz: su folio y su monto.
 * Si un alumno paga varias veces en el mismo mes, la celda tendrá
 * varias de estas (una por pago), para no perder ningún folio.
 *
 * Las marcas de Oficina y de Cortesía viajan por PAGO y no por celda ni
 * por renglón, porque así es como se guardan: un alumno puede tener dos
 * pagos en el mismo mes con uno marcado y el otro no. Pintar la celda
 * completa diría que todo el dinero se fue a Oficina, o que todo fue
 * regalado, cuando quizá solo una parte lo fue.
 */
public class CeldaMesDTO {

    private String folio;
    private BigDecimal monto;

    /** True si la comisión de este pago se le acredita a Oficina al 10%. */
    private boolean comisionOficina;

    /**
     * True si el modo de pago es "Cortesía": paquete regalado.
     *
     * Es dinero que NUNCA entró al cajón. Suma a ingresos pero no
     * comisiona y no cuenta como efectivo, así que en la matriz se pinta
     * distinto para que nadie lo lea como un cobro normal al cuadrar.
     */
    private boolean cortesia;

    public CeldaMesDTO(String folio, BigDecimal monto,
                       boolean comisionOficina, boolean cortesia) {
        this.folio = folio;
        this.monto = monto;
        this.comisionOficina = comisionOficina;
        this.cortesia = cortesia;
    }

    public String getFolio() {
        return folio;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public boolean isComisionOficina() {
        return comisionOficina;
    }

    public boolean isCortesia() {
        return cortesia;
    }
}