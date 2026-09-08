package com.cjv.sistemacjv.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un renglón de detalle para los reportes: sirve tanto para las
 * devoluciones como para las cortesías, porque las dos contestan
 * la misma pregunta con los mismos datos.
 *
 * En devoluciones responde "¿por qué le bajó la comisión a esta
 * vendedora?"; en cortesías, "¿a quién se le regaló y por qué?".
 *
 * El monto va siempre en POSITIVO. En la base de datos una devolución
 * vive en negativo, pero aquí es un número para leer en una tabla,
 * no para volver a sumar.
 */
public class MovimientoDetalleDTO {

    private LocalDate fecha;
    private String folio;
    private String alumno;
    private String escuela;          // carrera de la O.T., o "ADICIONAL"
    private BigDecimal monto;        // en positivo
    private String modalidad;        // por dónde se movió el dinero
    private String destinatario;     // vendedora afectada, u "Oficina"
    private String motivo;           // los comentarios del movimiento

    public MovimientoDetalleDTO() {
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public String getFolio() {
        return folio;
    }

    public void setFolio(String folio) {
        this.folio = folio;
    }

    public String getAlumno() {
        return alumno;
    }

    public void setAlumno(String alumno) {
        this.alumno = alumno;
    }

    public String getEscuela() {
        return escuela;
    }

    public void setEscuela(String escuela) {
        this.escuela = escuela;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public String getModalidad() {
        return modalidad;
    }

    public void setModalidad(String modalidad) {
        this.modalidad = modalidad;
    }

    public String getDestinatario() {
        return destinatario;
    }

    public void setDestinatario(String destinatario) {
        this.destinatario = destinatario;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }
}