package com.cjv.sistemacjv.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "cierre_caja")
public class CierreCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cierre_caja")
    private Integer idCierreCaja;

    /** Quién envió el cierre (el que contó el dinero). */
    @ManyToOne
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha_cierre", nullable = false)
    private LocalDate fechaCierre;

    @Column(name = "total_cierre", nullable = false)
    private BigDecimal totalCierre;

    /** Lo que el sistema calculó que debía haber en el cajón. */
    @Column(name = "efectivo_esperado", nullable = false)
    private BigDecimal efectivoEsperado = BigDecimal.ZERO;

    /** Lo que la persona contó físicamente. */
    @Column(name = "efectivo_contado", nullable = false)
    private BigDecimal efectivoContado = BigDecimal.ZERO;

    /** contado - esperado. Negativo = falta dinero. */
    @Column(name = "diferencia", nullable = false)
    private BigDecimal diferencia = BigDecimal.ZERO;

    @Column(name = "total_ingresos", nullable = false)
    private BigDecimal totalIngresos = BigDecimal.ZERO;

    @Column(name = "total_egresos", nullable = false)
    private BigDecimal totalEgresos = BigDecimal.ZERO;

    /** ENVIADO o AUTORIZADO. */
    @Column(name = "estado", nullable = false, length = 20)
    private String estado = "ENVIADO";

    /** Quién lo autorizó. Null mientras siga en ENVIADO. */
    @ManyToOne
    @JoinColumn(name = "id_usuario_autoriza")
    private Usuario usuarioAutoriza;

    @Column(name = "fecha_autorizacion")
    private LocalDateTime fechaAutorizacion;

    @Column(name = "comentarios", length = 255)
    private String comentarios;

    @Column(name = "activo")
    private Boolean activo = true;

    public CierreCaja() {
    }

    public Integer getIdCierreCaja() {
        return idCierreCaja;
    }

    public void setIdCierreCaja(Integer idCierreCaja) {
        this.idCierreCaja = idCierreCaja;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public LocalDate getFechaCierre() {
        return fechaCierre;
    }

    public void setFechaCierre(LocalDate fechaCierre) {
        this.fechaCierre = fechaCierre;
    }

    public BigDecimal getTotalCierre() {
        return totalCierre;
    }

    public void setTotalCierre(BigDecimal totalCierre) {
        this.totalCierre = totalCierre;
    }

    public BigDecimal getEfectivoEsperado() {
        return efectivoEsperado;
    }

    public void setEfectivoEsperado(BigDecimal efectivoEsperado) {
        this.efectivoEsperado = efectivoEsperado;
    }

    public BigDecimal getEfectivoContado() {
        return efectivoContado;
    }

    public void setEfectivoContado(BigDecimal efectivoContado) {
        this.efectivoContado = efectivoContado;
    }

    public BigDecimal getDiferencia() {
        return diferencia;
    }

    public void setDiferencia(BigDecimal diferencia) {
        this.diferencia = diferencia;
    }

    public BigDecimal getTotalIngresos() {
        return totalIngresos;
    }

    public void setTotalIngresos(BigDecimal totalIngresos) {
        this.totalIngresos = totalIngresos;
    }

    public BigDecimal getTotalEgresos() {
        return totalEgresos;
    }

    public void setTotalEgresos(BigDecimal totalEgresos) {
        this.totalEgresos = totalEgresos;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Usuario getUsuarioAutoriza() {
        return usuarioAutoriza;
    }

    public void setUsuarioAutoriza(Usuario usuarioAutoriza) {
        this.usuarioAutoriza = usuarioAutoriza;
    }

    public LocalDateTime getFechaAutorizacion() {
        return fechaAutorizacion;
    }

    public void setFechaAutorizacion(LocalDateTime fechaAutorizacion) {
        this.fechaAutorizacion = fechaAutorizacion;
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