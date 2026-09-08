package com.cjv.sistemacjv.dto;

import com.cjv.sistemacjv.entity.Contrato;
import com.cjv.sistemacjv.entity.Entrega;

import java.math.BigDecimal;

/**
 * Un renglón de la pantalla de Entregas: el alumno, lo que debe, y si
 * ya se le entregó o no.
 *
 * Junta tres cosas que viven en tablas distintas para que la pantalla
 * no tenga que cruzarlas ella misma.
 */
public class RenglonEntregaDTO {

    private Contrato contrato;

    /** Lo que ha pagado: anticipo + pagos activos. */
    private BigDecimal abonado;

    /** Lo que falta: total - abonado. */
    private BigDecimal resta;

    /** La entrega, si ya se hizo. Null = todavía no se le entrega. */
    private Entrega entrega;

    public RenglonEntregaDTO() {
    }

    public RenglonEntregaDTO(Contrato contrato,
                             BigDecimal abonado,
                             BigDecimal resta,
                             Entrega entrega) {
        this.contrato = contrato;
        this.abonado = abonado;
        this.resta = resta;
        this.entrega = entrega;
    }

    public Contrato getContrato() {
        return contrato;
    }

    public void setContrato(Contrato contrato) {
        this.contrato = contrato;
    }

    public BigDecimal getAbonado() {
        return abonado;
    }

    public void setAbonado(BigDecimal abonado) {
        this.abonado = abonado;
    }

    public BigDecimal getResta() {
        return resta;
    }

    public void setResta(BigDecimal resta) {
        this.resta = resta;
    }

    public Entrega getEntrega() {
        return entrega;
    }

    public void setEntrega(Entrega entrega) {
        this.entrega = entrega;
    }

    /** Atajo para la pantalla: true si ya se le entregó. */
    public boolean isEntregado() {
        return entrega != null;
    }
}