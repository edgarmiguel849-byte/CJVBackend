package com.cjv.sistemacjv.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Un renglón del apartado "Artículos Adquiridos" de un contrato adicional.
 *
 * Es como el renglón de un ticket: descripción y monto. La suma de todos
 * los renglones de un contrato es su TOTAL (por eso el campo Total quedó
 * en solo lectura en la pantalla: lo calcula el servidor, no el navegador).
 *
 * OJO: aquí NO hay un @ManyToOne hacia Contrato a propósito. Se guarda
 * el número del contrato pelón (idContrato). Si pusiéramos la relación,
 * al convertir esto a JSON el contrato traería sus artículos, y cada
 * artículo traería su contrato, y así hasta el infinito. Además, todas
 * las pantallas que hoy devuelven contratos empezarían a cargar artículos
 * sin necesitarlos.
 */
@Entity
@Table(name = "contrato_articulo")
public class ContratoArticulo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_contrato_articulo")
    private Integer idContratoArticulo;

    /** A qué contrato pertenece este renglón. */
    @Column(name = "id_contrato", nullable = false)
    private Integer idContrato;

    /** Qué se le vendió. Ej: "Anillo de graduación", "Grabado interior". */
    @Column(name = "descripcion", length = 200, nullable = false)
    private String descripcion;

    /**
     * Cuánto cuesta este renglón.
     *
     * Nunca negativo: las devoluciones NO son artículos, son pagos en
     * negativo con folio de la serie Z y viven en la tabla 'pago'.
     */
    @Column(name = "monto", nullable = false)
    private BigDecimal monto = BigDecimal.ZERO;

    /**
     * En qué posición se capturó (0, 1, 2...).
     *
     * Sirve para que al reabrir el contrato los renglones salgan en el
     * mismo orden en que se escribieron, y no revueltos.
     */
    @Column(name = "orden", nullable = false)
    private Integer orden = 0;

    public ContratoArticulo() {
    }

    public ContratoArticulo(Integer idContrato, String descripcion, BigDecimal monto, Integer orden) {
        this.idContrato = idContrato;
        this.descripcion = descripcion;
        this.monto = monto;
        this.orden = orden;
    }

    public Integer getIdContratoArticulo() {
        return idContratoArticulo;
    }

    public void setIdContratoArticulo(Integer idContratoArticulo) {
        this.idContratoArticulo = idContratoArticulo;
    }

    public Integer getIdContrato() {
        return idContrato;
    }

    public void setIdContrato(Integer idContrato) {
        this.idContrato = idContrato;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public Integer getOrden() {
        return orden;
    }

    public void setOrden(Integer orden) {
        this.orden = orden;
    }
}