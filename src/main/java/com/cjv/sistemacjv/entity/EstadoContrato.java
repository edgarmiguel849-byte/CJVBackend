package com.cjv.sistemacjv.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "estado_contrato")
public class EstadoContrato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estado_contrato")
    private Integer idEstadoContrato;

    @Column(name = "nombre_estado", nullable = false, length = 50)
    private String nombreEstado;

    @Column(name = "activo")
    private Boolean activo = true;

    public EstadoContrato() {
    }

    public Integer getIdEstadoContrato() {
        return idEstadoContrato;
    }

    public void setIdEstadoContrato(Integer idEstadoContrato) {
        this.idEstadoContrato = idEstadoContrato;
    }

    public String getNombreEstado() {
        return nombreEstado;
    }

    public void setNombreEstado(String nombreEstado) {
        this.nombreEstado = nombreEstado;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }
}