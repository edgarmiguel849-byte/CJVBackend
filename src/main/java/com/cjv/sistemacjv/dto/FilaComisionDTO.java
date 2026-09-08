package com.cjv.sistemacjv.dto;

import java.math.BigDecimal;

/**
 * Una fila del reporte de comisiones = un destinatario de comisión.
 * Puede ser una vendedora concreta o la fila especial "Oficina"
 * (para los contratos Adicionales, sin O.T.).
 */
public class FilaComisionDTO {

    // id del usuario vendedora, o null cuando la fila es "Oficina"
    private Integer idUsuario;

    // Nombre a mostrar: el de la vendedora, o "Oficina"
    private String nombre;

    // Porcentaje aplicado (10.00 = 10%). En Oficina se usa el porcentaje
    // configurado para la empresa (por ahora también 10%).
    private BigDecimal porcentaje;

    // Cuánto se cobró en el periodo que corresponde a este destinatario
    // (pagos + anticipos de sus contratos).
    private BigDecimal montoCobrado;

    // Comisión resultante = montoCobrado * porcentaje / 100
    private BigDecimal comision;

    public FilaComisionDTO() {
    }

    public FilaComisionDTO(Integer idUsuario, String nombre, BigDecimal porcentaje,
                           BigDecimal montoCobrado, BigDecimal comision) {
        this.idUsuario = idUsuario;
        this.nombre = nombre;
        this.porcentaje = porcentaje;
        this.montoCobrado = montoCobrado;
        this.comision = comision;
    }

    public Integer getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(Integer idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public BigDecimal getPorcentaje() {
        return porcentaje;
    }

    public void setPorcentaje(BigDecimal porcentaje) {
        this.porcentaje = porcentaje;
    }

    public BigDecimal getMontoCobrado() {
        return montoCobrado;
    }

    public void setMontoCobrado(BigDecimal montoCobrado) {
        this.montoCobrado = montoCobrado;
    }

    public BigDecimal getComision() {
        return comision;
    }

    public void setComision(BigDecimal comision) {
        this.comision = comision;
    }
}