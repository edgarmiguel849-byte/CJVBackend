package com.cjv.sistemacjv.dto;

import java.math.BigDecimal;

/**
 * Un renglón de la hoja física "Control de contratos y recibos de oficina".
 * Cada cobro del día ocupa una línea: contrato / recibo / nombre / escuela,
 * y su monto cae en la columna del destinatario que le toca.
 */
public class RenglonCorteDTO {

    // Columna CONTRATO: se llena solo en los anticipos (RN-04),
    // porque el anticipo no lleva folio de recibo.
    private String folioContrato;

    // Columna RECIBO: el folio del pago (los abonos sí llevan folio).
    private String folioRecibo;

    private String nombre;    // alumno
    private String escuela;   // carrera de la O.T., o "ADICIONAL" si no tiene

    // A quién le toca: null = Oficina.
    private Integer idDestinatario;
    private String nombreDestinatario;

    private BigDecimal monto;

    // Efectivo, Transferencia, Tarjeta, Depósito o Cortesía
    private String modalidad;

    // false = "números rojos" (transferencia, tarjeta, depósito)
    private boolean esEfectivo;

    /**
     * Paquete regalado. No es dinero: no va en ninguna columna de
     * destinatario, sino en su propia columna al final de la hoja.
     *
     * Hace falta esta marca porque una cortesía llega con idDestinatario
     * en null, igual que Oficina, y sin distinguirlas la pantalla pintaría
     * las cortesías dentro de la columna de Oficina.
     */
    private boolean esCortesia;

    /**
     * RN-11: dinero que SALIÓ porque el alumno pagó de más.
     * El monto viene en negativo, así que las sumas ya se ajustan solas.
     * Esta marca existe para que la pantalla y la hoja lo puedan pintar
     * distinto en vez de mostrar un número negativo suelto.
     */
    private boolean esDevolucion;

    // Los cancelados se muestran pero no comisionan.
    private boolean cancelado;

    public RenglonCorteDTO() {
    }

    public String getFolioContrato() {
        return folioContrato;
    }

    public void setFolioContrato(String folioContrato) {
        this.folioContrato = folioContrato;
    }

    public String getFolioRecibo() {
        return folioRecibo;
    }

    public void setFolioRecibo(String folioRecibo) {
        this.folioRecibo = folioRecibo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getEscuela() {
        return escuela;
    }

    public void setEscuela(String escuela) {
        this.escuela = escuela;
    }

    public Integer getIdDestinatario() {
        return idDestinatario;
    }

    public void setIdDestinatario(Integer idDestinatario) {
        this.idDestinatario = idDestinatario;
    }

    public String getNombreDestinatario() {
        return nombreDestinatario;
    }

    public void setNombreDestinatario(String nombreDestinatario) {
        this.nombreDestinatario = nombreDestinatario;
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

    public boolean isEsEfectivo() {
        return esEfectivo;
    }

    public void setEsEfectivo(boolean esEfectivo) {
        this.esEfectivo = esEfectivo;
    }

    public boolean isEsCortesia() {
        return esCortesia;
    }

    public void setEsCortesia(boolean esCortesia) {
        this.esCortesia = esCortesia;
    }

    public boolean isEsDevolucion() {
        return esDevolucion;
    }

    public void setEsDevolucion(boolean esDevolucion) {
        this.esDevolucion = esDevolucion;
    }

    public boolean isCancelado() {
        return cancelado;
    }

    public void setCancelado(boolean cancelado) {
        this.cancelado = cancelado;
    }
}