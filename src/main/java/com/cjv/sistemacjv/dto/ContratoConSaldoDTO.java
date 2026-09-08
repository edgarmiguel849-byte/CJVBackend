package com.cjv.sistemacjv.dto;

import com.cjv.sistemacjv.entity.Contrato;

import java.math.BigDecimal;

public class ContratoConSaldoDTO {

    private Contrato contrato;
    private BigDecimal abonado;
    private BigDecimal resta;

    // true si el día del contrato (fecha_contrato) ya tiene corte de caja
    // generado. La pantalla lo usa para esconder Editar/Eliminar a Mostrador.
    private boolean diaConCorte;

    public ContratoConSaldoDTO(Contrato contrato, BigDecimal abonado, BigDecimal resta,
                               boolean diaConCorte) {
        this.contrato = contrato;
        this.abonado = abonado;
        this.resta = resta;
        this.diaConCorte = diaConCorte;
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

    public boolean isDiaConCorte() {
        return diaConCorte;
    }
}