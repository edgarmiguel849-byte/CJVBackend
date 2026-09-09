package com.cjv.sistemacjv.dto;

import com.cjv.sistemacjv.entity.Egreso;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * El corte de UNA persona en UN día: el dinero que ella recibió y que
 * todavía no ha entregado en ningún corte.
 *
 * NO ES el reporte del Jefe. Aquí a propósito NO hay comisiones por
 * vendedora ni desglose por escuela: eso es de Reportes, que ve el día
 * completo de todos. La comisión es de la VENDEDORA de la O.T., mientras
 * que este corte es de quien RECIBIÓ el dinero — son dos personas
 * distintas y mezclarlas le atribuiría a alguien dinero que no es suyo.
 *
 * Lo que sí trae:
 *   efectivo      -> lo que debe haber en su cajón (esto es lo que cuenta)
 *   noEfectivo    -> transferencias, tarjeta: entró, pero no en billetes
 *   cortesías     -> paquetes que regaló; se listan pero NO suman dinero
 *   devoluciones  -> dinero que salió de vuelta al alumno
 *   egresos       -> lo que sacó del cajón para gastos
 *   renglones     -> el cuerpo de la hoja física que va a firmar
 */
public class CorteDePersonaDTO {

    private LocalDate fecha;
    private Integer idUsuario;
    private String nombreUsuario;

    /** Entró en billetes. Es la base del efectivo esperado. */
    private BigDecimal totalEfectivo = BigDecimal.ZERO;

    /** Entró por transferencia, tarjeta, etc. */
    private BigDecimal totalNoEfectivo = BigDecimal.ZERO;

    /** Paquetes regalados. Se muestran, pero no son dinero. */
    private BigDecimal totalCortesias = BigDecimal.ZERO;

    /** Dinero devuelto al alumno, en positivo para leerlo. */
    private BigDecimal totalDevoluciones = BigDecimal.ZERO;

    /** Todo lo positivo que entró, cortesías incluidas. */
    private BigDecimal totalIngresos = BigDecimal.ZERO;

    /** Lo que salió del cajón por gastos. */
    private BigDecimal totalEgresos = BigDecimal.ZERO;

    /**
     * Lo que debe haber físicamente en el cajón de esta persona:
     * efectivo que entró menos los egresos que ella capturó.
     *
     * Las devoluciones NO se restan aparte: vienen con monto negativo, así
     * que ya bajaron el efectivo cuando se sumaron.
     */
    private BigDecimal efectivoEnCaja = BigDecimal.ZERO;

    /** El cuerpo de la hoja: cada movimiento con su folio y su alumno. */
    private List<RenglonCorteDTO> renglones = new ArrayList<>();

    /** Los egresos, para listarlos aparte al pie de la hoja. */
    private List<Egreso> egresos = new ArrayList<>();

    /**
     * IDs de lo que este corte se va a llevar. Con estos se escriben los
     * renglones de cierre_caja_detalle al momento de entregar, y son la
     * prueba de que ese dinero ya se cortó.
     */
    private List<Integer> idsPagos = new ArrayList<>();
    private List<Integer> idsContratos = new ArrayList<>();
    private List<Integer> idsEgresos = new ArrayList<>();

    public CorteDePersonaDTO() {
    }

    /** ¿Hay algo que cortar? Sirve para no dejar entregar un corte vacío. */
    public boolean estaVacio() {
        return idsPagos.isEmpty() && idsContratos.isEmpty() && idsEgresos.isEmpty();
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public Integer getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(Integer idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public void setNombreUsuario(String nombreUsuario) {
        this.nombreUsuario = nombreUsuario;
    }

    public BigDecimal getTotalEfectivo() {
        return totalEfectivo;
    }

    public void setTotalEfectivo(BigDecimal totalEfectivo) {
        this.totalEfectivo = totalEfectivo;
    }

    public BigDecimal getTotalNoEfectivo() {
        return totalNoEfectivo;
    }

    public void setTotalNoEfectivo(BigDecimal totalNoEfectivo) {
        this.totalNoEfectivo = totalNoEfectivo;
    }

    public BigDecimal getTotalCortesias() {
        return totalCortesias;
    }

    public void setTotalCortesias(BigDecimal totalCortesias) {
        this.totalCortesias = totalCortesias;
    }

    public BigDecimal getTotalDevoluciones() {
        return totalDevoluciones;
    }

    public void setTotalDevoluciones(BigDecimal totalDevoluciones) {
        this.totalDevoluciones = totalDevoluciones;
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

    public BigDecimal getEfectivoEnCaja() {
        return efectivoEnCaja;
    }

    public void setEfectivoEnCaja(BigDecimal efectivoEnCaja) {
        this.efectivoEnCaja = efectivoEnCaja;
    }

    public List<RenglonCorteDTO> getRenglones() {
        return renglones;
    }

    public void setRenglones(List<RenglonCorteDTO> renglones) {
        this.renglones = renglones;
    }

    public List<Egreso> getEgresos() {
        return egresos;
    }

    public void setEgresos(List<Egreso> egresos) {
        this.egresos = egresos;
    }

    public List<Integer> getIdsPagos() {
        return idsPagos;
    }

    public void setIdsPagos(List<Integer> idsPagos) {
        this.idsPagos = idsPagos;
    }

    public List<Integer> getIdsContratos() {
        return idsContratos;
    }

    public void setIdsContratos(List<Integer> idsContratos) {
        this.idsContratos = idsContratos;
    }

    public List<Integer> getIdsEgresos() {
        return idsEgresos;
    }

    public void setIdsEgresos(List<Integer> idsEgresos) {
        this.idsEgresos = idsEgresos;
    }
}