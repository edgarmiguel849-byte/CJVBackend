package com.cjv.sistemacjv.service;

import com.cjv.sistemacjv.dto.CorteDiaDTO;
import com.cjv.sistemacjv.dto.RenglonCorteDTO;
import com.cjv.sistemacjv.dto.ReporteComisionesDTO;
import com.cjv.sistemacjv.entity.Egreso;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Arma la hoja de corte de un día: junta lo que ya calcula ComisionService
 * (ingresos, comisiones, efectivo y no efectivo) con los egresos capturados,
 * y saca el efectivo que debe haber físicamente en la caja.
 */
@Service
public class CorteDiaService {

    private final ComisionService comisionService;
    private final EgresoService egresoService;

    public CorteDiaService(ComisionService comisionService,
                           EgresoService egresoService) {
        this.comisionService = comisionService;
        this.egresoService = egresoService;
    }

    public CorteDiaDTO generar(LocalDate fecha) {
        if (fecha == null) {
            throw new IllegalArgumentException("Debes indicar la fecha del corte.");
        }

        // 1. Lo que entró ese día (mismo cálculo del reporte de comisiones,
        //    pero con el rango de un solo día).
        ReporteComisionesDTO ingresos = comisionService.generar(fecha, fecha);

        // 2. El detalle renglón por renglón (el cuerpo de la hoja física).
        List<RenglonCorteDTO> renglones = comisionService.detallar(fecha, fecha);

        // 3. Lo que salió ese día.
        List<Egreso> egresos = egresoService.listarPorRango(fecha, fecha);

        BigDecimal totalEgresos = egresos.stream()
                .map(Egreso::getMonto)
                .filter(m -> m != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. Efectivo que debe estar en el cajón:
        //    solo el dinero que entró en efectivo, menos lo que salió.
        BigDecimal efectivoIngresado = ingresos.getTotalEfectivo() != null
                ? ingresos.getTotalEfectivo()
                : BigDecimal.ZERO;

        BigDecimal efectivoEnCaja = efectivoIngresado.subtract(totalEgresos);

        CorteDiaDTO corte = new CorteDiaDTO();
        corte.setFecha(fecha);
        corte.setIngresos(ingresos);
        corte.setRenglones(renglones);
        corte.setEgresos(egresos);
        corte.setTotalEgresos(totalEgresos);
        corte.setEfectivoEnCaja(efectivoEnCaja);

        return corte;
    }
}