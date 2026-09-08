package com.cjv.sistemacjv.dto;

import com.cjv.sistemacjv.entity.Contrato;

import java.math.BigDecimal;

/**
 * Un resultado del buscador de alumno: el contrato encontrado más su saldo
 * calculado (abonado / resta). El frontend lee del contrato el nombre del
 * alumno, folio, O.T. y carrera.
 */
public class ResultadoBusquedaAlumnoDTO {

    private Contrato contrato;
    private BigDecimal abonado;
    private BigDecimal resta;

    public ResultadoBusquedaAlumnoDTO(Contrato contrato, BigDecimal abonado, BigDecimal resta) {
        this.contrato = contrato;
        this.abonado = abonado;
        this.resta = resta;
    }

    public Contrato getContrato() {
        return contrato;
    }

    public BigDecimal getAbonado() {
        return abonado;
    }

    public BigDecimal getResta() {
        return resta;
    }
}