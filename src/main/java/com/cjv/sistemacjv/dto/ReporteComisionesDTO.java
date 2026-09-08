package com.cjv.sistemacjv.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Reporte de un rango de fechas para la pantalla de Reportes.
 * Replica la lógica de la hoja física "Control de contratos y recibos de oficina":
 * cada renglón cobrado del periodo suma a la columna de su vendedora (o a Oficina),
 * y al final se saca el porcentaje de comisión de cada columna.
 *
 * Trae además los números que usa la contadora: ingresos por modalidad,
 * cortesías y devoluciones, cada uno con su detalle.
 */
public class ReporteComisionesDTO {

    private LocalDate desde;
    private LocalDate hasta;

    // Una fila por vendedora con movimiento en el periodo, más la fila "Oficina".
    private List<FilaComisionDTO> filas;

    // Totales al pie
    private BigDecimal totalCobrado;      // NETO: las devoluciones ya están restadas
    private BigDecimal totalComisiones;   // suma de todas las comisiones

    // Desglose de caja: cuánto fue efectivo y cuánto no (los "números rojos").
    // Sirve para cuadrar el arqueo del día.
    private BigDecimal totalEfectivo;
    private BigDecimal totalNoEfectivo;

    /**
     * Paquetes regalados del periodo. NO es dinero: no entró a la caja
     * ni al banco, y no comisiona a nadie. Se lleva aparte para tener
     * control de cuánto se está regalando.
     */
    private BigDecimal totalCortesias = BigDecimal.ZERO;

    /**
     * TODO lo positivo del periodo, INCLUYENDO las cortesías.
     *
     * Va en bruto a propósito: la contadora hace "ingresos menos cortesías"
     * para saber cuánto dinero real entró. Por eso no se le resta nada aquí.
     *
     * Ojo: este número NO coincide con totalCobrado, que va neto. La
     * diferencia entre los dos es justo totalDevoluciones.
     */
    private BigDecimal totalIngresos = BigDecimal.ZERO;

    /**
     * RN-11: dinero devuelto en el periodo, en POSITIVO (450.00, no -450.00),
     * porque es un número para mostrar, no para volver a sumar.
     */
    private BigDecimal totalDevoluciones = BigDecimal.ZERO;

    // Cuántos recibos y cuánto por cada modalidad (el desglose de Ingresos).
    private List<TotalPorModalidadDTO> ingresosPorModalidad = new ArrayList<>();

    // El detalle que se muestra al abrir cada botón.
    private List<MovimientoDetalleDTO> devoluciones = new ArrayList<>();
    private List<MovimientoDetalleDTO> cortesias = new ArrayList<>();

    public ReporteComisionesDTO() {
    }

    public LocalDate getDesde() {
        return desde;
    }

    public void setDesde(LocalDate desde) {
        this.desde = desde;
    }

    public LocalDate getHasta() {
        return hasta;
    }

    public void setHasta(LocalDate hasta) {
        this.hasta = hasta;
    }

    public List<FilaComisionDTO> getFilas() {
        return filas;
    }

    public void setFilas(List<FilaComisionDTO> filas) {
        this.filas = filas;
    }

    public BigDecimal getTotalCobrado() {
        return totalCobrado;
    }

    public void setTotalCobrado(BigDecimal totalCobrado) {
        this.totalCobrado = totalCobrado;
    }

    public BigDecimal getTotalComisiones() {
        return totalComisiones;
    }

    public void setTotalComisiones(BigDecimal totalComisiones) {
        this.totalComisiones = totalComisiones;
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

    public BigDecimal getTotalIngresos() {
        return totalIngresos;
    }

    public void setTotalIngresos(BigDecimal totalIngresos) {
        this.totalIngresos = totalIngresos;
    }

    public BigDecimal getTotalDevoluciones() {
        return totalDevoluciones;
    }

    public void setTotalDevoluciones(BigDecimal totalDevoluciones) {
        this.totalDevoluciones = totalDevoluciones;
    }

    public List<TotalPorModalidadDTO> getIngresosPorModalidad() {
        return ingresosPorModalidad;
    }

    public void setIngresosPorModalidad(List<TotalPorModalidadDTO> ingresosPorModalidad) {
        this.ingresosPorModalidad = ingresosPorModalidad;
    }

    public List<MovimientoDetalleDTO> getDevoluciones() {
        return devoluciones;
    }

    public void setDevoluciones(List<MovimientoDetalleDTO> devoluciones) {
        this.devoluciones = devoluciones;
    }

    public List<MovimientoDetalleDTO> getCortesias() {
        return cortesias;
    }

    public void setCortesias(List<MovimientoDetalleDTO> cortesias) {
        this.cortesias = cortesias;
    }
}