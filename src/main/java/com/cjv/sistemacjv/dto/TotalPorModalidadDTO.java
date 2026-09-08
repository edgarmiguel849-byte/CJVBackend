package com.cjv.sistemacjv.dto;

import java.math.BigDecimal;

/**
 * Un renglón del desglose de ingresos: cuánto entró por cada modalidad
 * y con cuántos recibos.
 *
 * Es lo que le sirve a la contadora para separar el efectivo del banco,
 * y para restar las cortesías del total de ingresos.
 */
public class TotalPorModalidadDTO {

    private String modalidad;     // Efectivo, Transferencia, Tarjeta, Depósito, Cortesía
    private int cantidad;         // cuántos recibos
    private BigDecimal monto;

    public TotalPorModalidadDTO() {
    }

    public TotalPorModalidadDTO(String modalidad, int cantidad, BigDecimal monto) {
        this.modalidad = modalidad;
        this.cantidad = cantidad;
        this.monto = monto;
    }

    public String getModalidad() {
        return modalidad;
    }

    public void setModalidad(String modalidad) {
        this.modalidad = modalidad;
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }
}