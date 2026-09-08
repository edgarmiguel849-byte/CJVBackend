package com.cjv.sistemacjv.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un renglón del reporte de control de folios.
 *
 * VERDE = el folio se usó: existe un pago o un contrato con ese número.
 * ROJO  = el folio no se usó: está en la tabla folio_no_utilizado, que es
 *         donde se registran los papeles que se echaron a perder.
 *
 * OJO con qué NO decide el color: ni el estado del contrato, ni si está
 * vencido, ni si tiene saldo. Un contrato CANCELADO que sí se cobró va
 * VERDE, porque el folio se usó. Lo único que se pregunta es si ese papel
 * llegó a existir.
 */
public class FolioControlDTO {

    private String folio;

    /** true = verde (se usó), false = rojo (no se usó). */
    private boolean usado;

    /**
     * La fecha del papel. En los verdes es la del pago o del contrato; en
     * los rojos es la que se capturó al marcarlo.
     *
     * Es lo que permite filtrar por periodo. Sin ella, un folio no cobrado
     * quedaría fuera de cualquier búsqueda por fechas.
     */
    private LocalDate fecha;

    /** Nombre del alumno, o el motivo. Vacío en los rojos. */
    private String descripcion;

    /** El monto, solo en los verdes. */
    private BigDecimal monto;

    public FolioControlDTO(String folio, boolean usado, LocalDate fecha,
                           String descripcion, BigDecimal monto) {
        this.folio = folio;
        this.usado = usado;
        this.fecha = fecha;
        this.descripcion = descripcion;
        this.monto = monto;
    }

    public String getFolio() {
        return folio;
    }

    public boolean isUsado() {
        return usado;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public BigDecimal getMonto() {
        return monto;
    }
}