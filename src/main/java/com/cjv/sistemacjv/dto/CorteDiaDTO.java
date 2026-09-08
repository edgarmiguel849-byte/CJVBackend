package com.cjv.sistemacjv.dto;

import com.cjv.sistemacjv.entity.Egreso;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Hoja de corte de un día, replicando el papel
 * "Control de contratos y recibos de oficina":
 *
 *   Ingresos del día (efectivo)  -  Egresos capturados  =  Efectivo en caja
 *
 * Los "números rojos" del papel (transferencias, tarjeta, depósito) NO se
 * capturan a mano: el sistema ya sabe cuáles fueron porque cada pago tiene
 * su modo, y se descuentan solos del efectivo.
 */
public class CorteDiaDTO {

    private LocalDate fecha;

    // Todo lo que entró: filas por destinataria, comisiones, efectivo/no efectivo.
    private ReporteComisionesDTO ingresos;

    // Detalle renglón por renglón, como el cuerpo de la hoja física.
    private List<RenglonCorteDTO> renglones;

    // Lo que salió físicamente de la caja (capturado en la pantalla de Egresos).
    private List<Egreso> egresos;
    private BigDecimal totalEgresos;

    // El número que se compara contra el conteo físico del cajón.
    private BigDecimal efectivoEnCaja;

    public CorteDiaDTO() {
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public ReporteComisionesDTO getIngresos() {
        return ingresos;
    }

    public void setIngresos(ReporteComisionesDTO ingresos) {
        this.ingresos = ingresos;
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
}