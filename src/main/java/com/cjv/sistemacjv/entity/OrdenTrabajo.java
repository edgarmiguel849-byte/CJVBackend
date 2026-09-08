package com.cjv.sistemacjv.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "orden_trabajo")
public class OrdenTrabajo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_orden_trabajo")
    private Integer idOrdenTrabajo;

    @Column(name = "numero", nullable = false)
    private Integer numero;

    @Column(name = "anio", nullable = false)
    private Integer anio;

    // ===== Datos de la escuela/carrera capturados a mano (migración A) =====
    // Antes vivían en las tablas 'escuela' y 'carrera'. Ahora se escriben
    // directo en la O.T. La relación vieja @ManyToOne a Carrera se eliminó.
    @Column(name = "escuela", length = 150)
    private String escuela;

    @Column(name = "siglas", length = 30)
    private String siglas;

    @Column(name = "carrera_texto", length = 150)
    private String carreraTexto;

    // Escolarizado / Abierto
    @Column(name = "modalidad", length = 30)
    private String modalidad;

    // Texto libre: "martes y jueves", "los días 15", etc.
    @Column(name = "dias_cobro", length = 100)
    private String diasCobro;
    // =======================================================================

    @Column(name = "grupo", length = 50)
    private String grupo;

    @Column(name = "generacion", length = 20)
    private String generacion;

    @ManyToOne
    @JoinColumn(name = "id_vendedor")
    private Vendedor vendedor;

    @Column(name = "fecha_entrega")
    private LocalDate fechaEntrega;

    // ===== RECORRIMIENTO DE LA ENTREGA =====
    //
    // Recorrer = mover la fecha de entrega ya comprometida con la escuela.
    // No es un cambio cualquiera: es un compromiso que se rompe, por eso
    // queda marcado y por eso se guarda de cuándo era.
    //
    // Las DOS las llena el SERVIDOR en OrdenTrabajoService.editar(),
    // comparando la fecha que llega contra la que había. El navegador NO
    // las manda: si las mandara, alguien podría prender el rojo sin haber
    // movido nada, o moverla sin que se note.

    /** True cuando la entrega ya se recorrió al menos una vez. Pinta el rojo. */
    @Column(name = "entrega_recorrida", nullable = false)
    private Boolean entregaRecorrida = false;

    /**
     * La fecha que se prometió la PRIMERA vez.
     *
     * Se escribe una sola vez, en el primer recorrimiento, y ya no se pisa.
     * Si un grupo se recorre tres veces, "original" tiene que seguir siendo
     * la primera: es la que trae la escuela en el papel cuando reclama.
     * Queda en null mientras la entrega nunca se haya movido.
     */
    @Column(name = "fecha_entrega_original")
    private LocalDate fechaEntregaOriginal;
    // =======================================

    @Column(name = "fecha_cierre_ciclo")
    private LocalDate fechaCierreCiclo;

    @Column(name = "activo")
    private Boolean activo = true;

    public OrdenTrabajo() {}

    public Integer getIdOrdenTrabajo() { return idOrdenTrabajo; }
    public void setIdOrdenTrabajo(Integer idOrdenTrabajo) { this.idOrdenTrabajo = idOrdenTrabajo; }

    public Integer getNumero() { return numero; }
    public void setNumero(Integer numero) { this.numero = numero; }

    public Integer getAnio() { return anio; }
    public void setAnio(Integer anio) { this.anio = anio; }

    public String getEscuela() { return escuela; }
    public void setEscuela(String escuela) { this.escuela = escuela; }

    public String getSiglas() { return siglas; }
    public void setSiglas(String siglas) { this.siglas = siglas; }

    public String getCarreraTexto() { return carreraTexto; }
    public void setCarreraTexto(String carreraTexto) { this.carreraTexto = carreraTexto; }

    public String getModalidad() { return modalidad; }
    public void setModalidad(String modalidad) { this.modalidad = modalidad; }

    public String getDiasCobro() { return diasCobro; }
    public void setDiasCobro(String diasCobro) { this.diasCobro = diasCobro; }

    public String getGrupo() { return grupo; }
    public void setGrupo(String grupo) { this.grupo = grupo; }

    public String getGeneracion() { return generacion; }
    public void setGeneracion(String generacion) { this.generacion = generacion; }

    public Vendedor getVendedor() { return vendedor; }
    public void setVendedor(Vendedor vendedor) { this.vendedor = vendedor; }

    public LocalDate getFechaEntrega() { return fechaEntrega; }
    public void setFechaEntrega(LocalDate fechaEntrega) { this.fechaEntrega = fechaEntrega; }

    public Boolean getEntregaRecorrida() { return entregaRecorrida; }
    public void setEntregaRecorrida(Boolean entregaRecorrida) { this.entregaRecorrida = entregaRecorrida; }

    /** Atajo para no andar preguntando por null en cada pantalla. */
    public boolean esEntregaRecorrida() {
        return Boolean.TRUE.equals(entregaRecorrida);
    }

    public LocalDate getFechaEntregaOriginal() { return fechaEntregaOriginal; }
    public void setFechaEntregaOriginal(LocalDate fechaEntregaOriginal) { this.fechaEntregaOriginal = fechaEntregaOriginal; }

    public LocalDate getFechaCierreCiclo() { return fechaCierreCiclo; }
    public void setFechaCierreCiclo(LocalDate fechaCierreCiclo) { this.fechaCierreCiclo = fechaCierreCiclo; }

    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
}