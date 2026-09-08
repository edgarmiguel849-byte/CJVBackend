package com.cjv.sistemacjv.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "pago")
public class Pago {

    /** Un abono normal: dinero que entra. */
    public static final String TIPO_ABONO = "Abono";

    /** Una devolución: dinero que sale porque el alumno pagó de más. */
    public static final String TIPO_DEVOLUCION = "Devolución";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pago")
    private Integer idPago;

    @ManyToOne
    @JoinColumn(name = "id_contrato", nullable = false)
    private Contrato contrato;

    /** Quién capturó el movimiento (mostrador o jefe). */
    @ManyToOne
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "folio", length = 20, unique = true)
    private String folio;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDate fechaPago;

    @Column(name = "monto_pago", nullable = false)
    private BigDecimal montoPago;

    @Column(name = "modo_pago", length = 50)
    private String modoPago;

    /**
     * Abono o Devolución. Contesta QUÉ es el movimiento.
     * No confundir con modoPago, que contesta POR DÓNDE se movió el dinero
     * (efectivo, transferencia, tarjeta, depósito).
     */
    @Column(name = "tipo_movimiento", length = 20, nullable = false)
    private String tipoMovimiento = TIPO_ABONO;

    /**
     * "Este cobro se le acredita a OFICINA, no a la vendedora."
     *
     * Se marca a mano, movimiento por movimiento, por decisión de quien cobra.
     * Cuando está en true, la comisión se va completa a Oficina al 10%, sin
     * importar qué vendedora traiga la Orden de Trabajo del contrato.
     *
     * Aplica igual a abonos y a devoluciones: si un pago se acreditó a Oficina
     * y después se devuelve, la devolución también debe marcarse, para que el
     * dinero salga de donde entró. Si no, la vendedora terminaría con un saldo
     * negativo por un dinero que nunca se le contó.
     *
     * Nace en false: todo lo que ya existía en la base se sigue comportando
     * exactamente igual que antes.
     */
    @Column(name = "comision_oficina", nullable = false)
    private Boolean comisionOficina = false;

    /**
     * Qué jefe autorizó la devolución, presente en el momento.
     * Va en null en los abonos normales.
     */
    @ManyToOne
    @JoinColumn(name = "id_usuario_autoriza")
    private Usuario usuarioAutoriza;

    /**
     * Contraseña que teclea el jefe al autorizar una devolución.
     *
     * NO se guarda en la base de datos (@Transient): solo viaja del
     * navegador al servidor, se compara y se descarta.
     *
     * WRITE_ONLY hace que Jackson la acepte al recibir pero nunca la
     * devuelva al consultar, para que no se asome en ninguna respuesta.
     */
    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String contrasenaAutoriza;

    @Column(name = "comentarios", length = 255)
    private String comentarios;

    @Column(name = "activo")
    private Boolean activo = true;

    public Pago() {
    }

    /** Atajo para no andar comparando textos por todo el sistema. */
    @Transient
    public boolean esDevolucion() {
        return TIPO_DEVOLUCION.equalsIgnoreCase(
                tipoMovimiento != null ? tipoMovimiento.trim() : null);
    }

    /**
     * Atajo para no andar preguntando si el valor viene vacío.
     * Un pago sin marca (null) cuenta como NO marcado.
     */
    @Transient
    public boolean esComisionOficina() {
        return Boolean.TRUE.equals(comisionOficina);
    }

    public Integer getIdPago() {
        return idPago;
    }

    public void setIdPago(Integer idPago) {
        this.idPago = idPago;
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

    public String getFolio() {
        return folio;
    }

    public void setFolio(String folio) {
        this.folio = folio;
    }

    public LocalDate getFechaPago() {
        return fechaPago;
    }

    public void setFechaPago(LocalDate fechaPago) {
        this.fechaPago = fechaPago;
    }

    public BigDecimal getMontoPago() {
        return montoPago;
    }

    public void setMontoPago(BigDecimal montoPago) {
        this.montoPago = montoPago;
    }

    public String getModoPago() {
        return modoPago;
    }

    public void setModoPago(String modoPago) {
        this.modoPago = modoPago;
    }

    public String getTipoMovimiento() {
        return tipoMovimiento;
    }

    public void setTipoMovimiento(String tipoMovimiento) {
        this.tipoMovimiento = tipoMovimiento;
    }

    public Boolean getComisionOficina() {
        return comisionOficina;
    }

    public void setComisionOficina(Boolean comisionOficina) {
        this.comisionOficina = comisionOficina;
    }

    public Usuario getUsuarioAutoriza() {
        return usuarioAutoriza;
    }

    public void setUsuarioAutoriza(Usuario usuarioAutoriza) {
        this.usuarioAutoriza = usuarioAutoriza;
    }

    public String getContrasenaAutoriza() {
        return contrasenaAutoriza;
    }

    public void setContrasenaAutoriza(String contrasenaAutoriza) {
        this.contrasenaAutoriza = contrasenaAutoriza;
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