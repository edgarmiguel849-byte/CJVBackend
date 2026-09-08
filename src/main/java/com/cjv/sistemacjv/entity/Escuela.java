package com.cjv.sistemacjv.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "escuela")
public class Escuela {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_escuela")
    private Integer idEscuela;

    @Column(name = "nombre_escuela", nullable = false, length = 60)
    private String nombreEscuela;

    public Escuela() {
    }

    public Integer getIdEscuela() {
        return idEscuela;
    }

    public void setIdEscuela(Integer idEscuela) {
        this.idEscuela = idEscuela;
    }

    public String getNombreEscuela() {
        return nombreEscuela;
    }

    public void setNombreEscuela(String nombreEscuela) {
        this.nombreEscuela = nombreEscuela;
    }
}