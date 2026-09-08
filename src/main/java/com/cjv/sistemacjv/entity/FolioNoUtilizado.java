package com.cjv.sistemacjv.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * Un folio del talonario que NO se usó: se echó a perder, se anuló, o
 * simplemente nunca se llenó. Solo se guarda el folio y la fecha en que
 * se detectó.
 *
 * POR QUÉ VIVE EN SU PROPIA TABLA Y NO EN 'contrato' NI EN 'pago':
 *
 * Esas dos exigen columnas que este registro no tiene ni puede inventar.
 * Un contrato obliga a paquete, estado, total y modo de anticipo; un pago
 * obliga a contrato padre, monto y tipo de movimiento. Meterlo ahí
 * significaría rellenar con datos falsos, y esos contratos fantasma
 * saldrían en la matriz de la O.T., en el corte del día y en las
 * comisiones.
 *
 * Aquí no toca nada de eso. No entra a ningún corte, no comisiona, no
 * aparece en ninguna matriz. Solo lo lee el reporte de control de folios.
 */
@Entity
@Table(name = "folio_no_utilizado")
public class FolioNoUtilizado {

    /**
     * De qué talonario es el folio.
     *
     * Es parte de la llave junto con el folio, NUNCA el folio solo: el
     * C2672 de recibos y el C2672 de contratos son papeles distintos que
     * casualmente comparten nombre. Cada tipo lleva su propio recorrido
     * por el abecedario, de la A a la Y (la Z está apartada para
     * devoluciones).
     */
    public static final String TIPO_RECIBO = "RECIBO";
    public static final String TIPO_CONTRATO = "CONTRATO";
    public static final String TIPO_ADICIONAL = "ADICIONAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_folio_no_utilizado")
    private Integer idFolioNoUtilizado;

    @Column(name = "tipo", length = 20, nullable = false)
    private String tipo;

    /** Formato letra + 4 dígitos, en mayúscula. Ejemplo: F6952. */
    @Column(name = "folio", length = 10, nullable = false)
    private String folio;

    /**
     * La fecha en que se registró el folio muerto.
     *
     * NO es adorno: es lo único que permite que el reporte filtre por
     * periodo. Un folio sin cobro no tiene otra fecha de dónde agarrarse,
     * así que sin esto los rojos quedarían fuera de cualquier filtro.
     */
    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    /** Quién lo capturó. Se toma del token, no del navegador. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    public FolioNoUtilizado() {
    }

    public Integer getIdFolioNoUtilizado() {
        return idFolioNoUtilizado;
    }

    public void setIdFolioNoUtilizado(Integer idFolioNoUtilizado) {
        this.idFolioNoUtilizado = idFolioNoUtilizado;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getFolio() {
        return folio;
    }

    public void setFolio(String folio) {
        this.folio = folio;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }
}