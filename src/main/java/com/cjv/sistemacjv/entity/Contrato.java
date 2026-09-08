package com.cjv.sistemacjv.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "contrato")
public class Contrato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_contrato")
    private Integer idContrato;

    @ManyToOne
    @JoinColumn(name = "id_cliente")
    private Cliente cliente;

    @ManyToOne
    @JoinColumn(name = "id_paquete", nullable = false)
    private Paquete paquete;

    @ManyToOne
    @JoinColumn(name = "id_estado_contrato", nullable = false)
    private EstadoContrato estadoContrato;

    @ManyToOne
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    // ===== Campos nuevos (migración 001 - Órdenes de Trabajo) =====

    @Column(name = "folio", length = 20, unique = true)
    private String folio;

    @ManyToOne
    @JoinColumn(name = "id_orden_trabajo")
    private OrdenTrabajo ordenTrabajo;

    @Column(name = "numero_lista")
    private Integer numeroLista;

    // ==============================================================

    // ===== Datos del alumno capturados a mano (migración A) =====
    // Antes vivían en la tabla 'cliente'. Ahora se escriben directo en
    // el contrato. La relación @ManyToOne a Cliente se conserva por ahora
    // (se limpiará después); estos campos son la nueva fuente de la verdad.
    @Column(name = "nombre_alumno", length = 150)
    private String nombreAlumno;

    @Column(name = "telefono1", length = 20)
    private String telefono1;

    @Column(name = "telefono2", length = 20)
    private String telefono2;

    @Column(name = "correo", length = 100)
    private String correo;
    // ============================================================

    // ===== Campo 'Ad:' exclusivo para contratos adicionales =====
    // Texto libre que solo se captura cuando el contrato es adicional
    // (sin O.T.). Se muestra en la lista de contratos y en Buscar Alumno.
    @Column(name = "ad", length = 255)
    private String ad;
    // ============================================================

    // ===== Datos del adicional (migración adicionales) =====
    // Carrera, escuela y generación de CADA contrato adicional. En los
    // contratos de grupo estos datos viven en la O.T. (compartida); el
    // adicional, al no tener O.T. propia, los guarda aquí en su registro.
    @Column(name = "carrera", length = 150)
    private String carrera;

    @Column(name = "escuela", length = 150)
    private String escuela;

    @Column(name = "generacion", length = 20)
    private String generacion;
    // =======================================================

    @Column(name = "fecha_contrato", nullable = false)
    private LocalDate fechaContrato;

    // ===== Fecha de entrega (migración adicionales) =====
    // Independiente de la fecha del contrato: es cuándo el cliente pide
    // que le entreguen. Se usa sobre todo en los adicionales.
    @Column(name = "fecha_entrega")
    private LocalDate fechaEntrega;
    // ====================================================

    @Column(name = "total", nullable = false)
    private BigDecimal total;

    @Column(name = "anticipo")
    private BigDecimal anticipo = BigDecimal.ZERO;

    // ===== Campo nuevo (migración 003) =====
    // Cómo se pagó el anticipo: Efectivo, Transferencia, Tarjeta o Depósito.
    // Sirve para separar efectivo vs no efectivo en el reporte de caja del día.
    @Column(name = "modo_anticipo", length = 50, nullable = false)
    private String modoAnticipo = "Efectivo";
    // =======================================

    // ===== Recontratación / traspaso (migración recontratación) =====

    /**
     * Saldo que viene ARRASTRADO de un contrato anterior.
     *
     * NO es dinero que entró hoy: por eso no cuenta en el corte de caja ni
     * comisiona (ese dinero ya se cobró y se comisionó en el contrato viejo).
     * Solo sirve para que el saldo del alumno salga correcto.
     *
     * Vacío = cero arrastre, que es como están todos los contratos normales.
     */
    @Column(name = "abono_heredado")
    private BigDecimal abonoHeredado;

    /**
     * Folio del contrato NUEVO al que se traspasó este contrato.
     *
     * OJO: un contrato traspasado SIGUE VIVO (activo = 1) y se queda en su
     * O.T.; solo se pinta morado para que se vea que ya se recontrató.
     * Esto es distinto del traspaso viejo entre adicionales, donde el
     * anterior se daba de baja y desaparecía.
     */
    @Column(name = "traspasado_a", length = 10)
    private String traspasadoA;
    // ================================================================

    // ===== Artículos adquiridos (migración artículos) =====

    /**
     * Los renglones de "Artículos Adquiridos" que viajan CON el contrato.
     *
     * @Transient significa: esto NO es una columna de la tabla 'contrato'.
     * Hibernate lo ignora por completo al guardar y al leer. Los artículos
     * viven en su propia tabla ('contrato_articulo') y se manejan aparte,
     * en ContratoArticuloService.
     *
     * Este campo es nada más el sobre en el que llegan: el navegador manda
     * el contrato con su lista adentro, el servicio saca la lista, calcula
     * el TOTAL sumándola, y la guarda en la otra tabla. El sobre se tira.
     *
     * Por eso también es seguro para el JAR viejo del puerto 8080: como no
     * es columna, ese sistema ni se entera de que existe.
     */
    @Transient
    private List<ContratoArticulo> articulos;
    // ======================================================

    @Column(name = "observaciones", length = 255)
    private String observaciones;

    @Column(name = "activo")
    private Boolean activo = true;

    public Contrato() {
    }

    /**
     * Con cuánto dinero ARRANCA este contrato, antes de sus pagos:
     * el arrastre heredado más el anticipo.
     *
     * Este método existe para que la fórmula del saldo viva en UN solo
     * lugar. Todos los servicios deben usarlo en vez de sumar a mano,
     * porque si uno se olvida del arrastre, esa pantalla le va a cobrar
     * de más al alumno.
     *
     *     Abonado = getAbonadoInicial() + sus pagos
     */
    @Transient
    public BigDecimal getAbonadoInicial() {
        BigDecimal a = anticipo != null ? anticipo : BigDecimal.ZERO;
        BigDecimal h = abonoHeredado != null ? abonoHeredado : BigDecimal.ZERO;
        return a.add(h);
    }

    /** ¿Este contrato ya se recontrató en otro folio? (se pinta morado) */
    @Transient
    public boolean esTraspasado() {
        return traspasadoA != null && !traspasadoA.isBlank();
    }

    public Integer getIdContrato() {
        return idContrato;
    }

    public void setIdContrato(Integer idContrato) {
        this.idContrato = idContrato;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
    }

    public Paquete getPaquete() {
        return paquete;
    }

    public void setPaquete(Paquete paquete) {
        this.paquete = paquete;
    }

    public EstadoContrato getEstadoContrato() {
        return estadoContrato;
    }

    public void setEstadoContrato(EstadoContrato estadoContrato) {
        this.estadoContrato = estadoContrato;
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

    public OrdenTrabajo getOrdenTrabajo() {
        return ordenTrabajo;
    }

    public void setOrdenTrabajo(OrdenTrabajo ordenTrabajo) {
        this.ordenTrabajo = ordenTrabajo;
    }

    public Integer getNumeroLista() {
        return numeroLista;
    }

    public void setNumeroLista(Integer numeroLista) {
        this.numeroLista = numeroLista;
    }

    public String getNombreAlumno() {
        return nombreAlumno;
    }

    public void setNombreAlumno(String nombreAlumno) {
        this.nombreAlumno = nombreAlumno;
    }

    public String getTelefono1() {
        return telefono1;
    }

    public void setTelefono1(String telefono1) {
        this.telefono1 = telefono1;
    }

    public String getTelefono2() {
        return telefono2;
    }

    public void setTelefono2(String telefono2) {
        this.telefono2 = telefono2;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getAd() {
        return ad;
    }

    public void setAd(String ad) {
        this.ad = ad;
    }

    public String getCarrera() {
        return carrera;
    }

    public void setCarrera(String carrera) {
        this.carrera = carrera;
    }

    public String getEscuela() {
        return escuela;
    }

    public void setEscuela(String escuela) {
        this.escuela = escuela;
    }

    public String getGeneracion() {
        return generacion;
    }

    public void setGeneracion(String generacion) {
        this.generacion = generacion;
    }

    public LocalDate getFechaContrato() {
        return fechaContrato;
    }

    public void setFechaContrato(LocalDate fechaContrato) {
        this.fechaContrato = fechaContrato;
    }

    public LocalDate getFechaEntrega() {
        return fechaEntrega;
    }

    public void setFechaEntrega(LocalDate fechaEntrega) {
        this.fechaEntrega = fechaEntrega;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public BigDecimal getAnticipo() {
        return anticipo;
    }

    public void setAnticipo(BigDecimal anticipo) {
        this.anticipo = anticipo;
    }

    public BigDecimal getAbonoHeredado() {
        return abonoHeredado;
    }

    public void setAbonoHeredado(BigDecimal abonoHeredado) {
        this.abonoHeredado = abonoHeredado;
    }

    public String getTraspasadoA() {
        return traspasadoA;
    }

    public void setTraspasadoA(String traspasadoA) {
        this.traspasadoA = traspasadoA;
    }

    public List<ContratoArticulo> getArticulos() {
        return articulos;
    }

    public void setArticulos(List<ContratoArticulo> articulos) {
        this.articulos = articulos;
    }

    public String getModoAnticipo() {
        return modoAnticipo;
    }

    public void setModoAnticipo(String modoAnticipo) {
        this.modoAnticipo = modoAnticipo;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }
}