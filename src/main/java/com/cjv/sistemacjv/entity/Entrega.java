package com.cjv.sistemacjv.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "entrega")
public class Entrega {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_entrega")
    private Integer idEntrega;

    @ManyToOne
    @JoinColumn(name = "id_contrato", nullable = false)
    private Contrato contrato;

    /** Quién le dio los cuadros al alumno. */
    @ManyToOne
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha_entrega", nullable = false)
    private LocalDate fechaEntrega;

    @Column(name = "estado_entrega", nullable = false, length = 50)
    private String estadoEntrega;

    /**
     * Lo que el alumno debía al momento de la entrega. Queda congelado:
     * si después paga, este número no cambia. Es lo que revisa el jefe.
     */
    @Column(name = "saldo_al_entregar", nullable = false)
    private BigDecimal saldoAlEntregar = BigDecimal.ZERO;

    @Column(name = "comentarios", length = 255)
    private String comentarios;

    @Column(name = "activo")
    private Boolean activo = true;

    public Entrega() {
    }

    public Integer getIdEntrega() {
        return idEntrega;
    }

    public void setIdEntrega(Integer idEntrega) {
        this.idEntrega = idEntrega;
    }

    public Contrato getContrato() {
        return contrato;
    }

    public void setContrato(Contrato contrato) {
        this.contrato = contrato;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public LocalDate getFechaEntrega() {
        return fechaEntrega;
    }

    public void setFechaEntrega(LocalDate fechaEntrega) {
        this.fechaEntrega = fechaEntrega;
    }

    public String getEstadoEntrega() {
        return estadoEntrega;
    }

    public void setEstadoEntrega(String estadoEntrega) {
        this.estadoEntrega = estadoEntrega;
    }

    public BigDecimal getSaldoAlEntregar() {
        return saldoAlEntregar;
    }

    public void setSaldoAlEntregar(BigDecimal saldoAlEntregar) {
        this.saldoAlEntregar = saldoAlEntregar;
    }

    public String getComentarios() {
        return comentarios;
    }

    public void setComentarios(String comentarios) {
        this.comentarios = comentarios;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }
}