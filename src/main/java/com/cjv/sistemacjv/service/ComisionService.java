package com.cjv.sistemacjv.service;

import com.cjv.sistemacjv.dto.FilaComisionDTO;
import com.cjv.sistemacjv.dto.MovimientoDetalleDTO;
import com.cjv.sistemacjv.dto.RenglonCorteDTO;
import com.cjv.sistemacjv.dto.ReporteComisionesDTO;
import com.cjv.sistemacjv.dto.TotalPorModalidadDTO;
import com.cjv.sistemacjv.entity.Contrato;
import com.cjv.sistemacjv.entity.OrdenTrabajo;
import com.cjv.sistemacjv.entity.Pago;
import com.cjv.sistemacjv.entity.Vendedor;
import com.cjv.sistemacjv.repository.ContratoRepository;
import com.cjv.sistemacjv.repository.PagoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calcula el reporte de un rango de fechas.
 *
 * Regla (RN-06 + aclaración del cierre de ciclo):
 *  - Cada monto cobrado en el periodo (pago o anticipo de contrato firmado)
 *    comisiona a quien corresponde.
 *  - Contrato CON O.T.  -> a la vendedora de esa O.T., con SU porcentaje.
 *  - Contrato SIN O.T. (Adicional) -> a Oficina.
 *  - O.T. sin vendedora asignada -> a Oficina (no se pierde el dinero).
 *  - Contrato cancelado -> NO comisiona (igual que en la matriz).
 *
 * PAGO MARCADO A OFICINA: quien cobra puede marcar un movimiento para que su
 * comisión se vaya COMPLETA a Oficina al 10%, aunque el contrato traiga
 * vendedora. Es una decisión humana, caso por caso, y pasa por encima de las
 * reglas de arriba. Aplica a abonos y a devoluciones por igual.
 * Solo afecta A QUIÉN se le acredita: el dinero entró a la caja igual, así que
 * los totales de efectivo, no efectivo e ingresos no se mueven.
 *
 * CORTESÍA: es un paquete regalado. Reduce lo que debe el alumno, pero el
 * dinero NO existe: no comisiona y no cuenta como efectivo ni como banco.
 * Sí entra en totalIngresos, porque la contadora hace "ingresos menos
 * cortesías" para llegar al dinero real.
 *
 * DEVOLUCIÓN (RN-11): viene con el monto en NEGATIVO, así que al sumarla
 * resta sola de la comisión y del efectivo. Aparte se acumula en positivo
 * para mostrarla como su propio total.
 */
@Service
public class ComisionService {

    // Clave que usamos en el mapa para agrupar todo lo de Oficina.
    private static final Integer CLAVE_OFICINA = -1;

    // Porcentaje que se aplica a la comisión de Oficina (por ahora 10%).
    private static final BigDecimal PORCENTAJE_OFICINA = new BigDecimal("10.00");

    /** Modo de pago que representa un paquete regalado. */
    public static final String MODO_CORTESIA = "Cortesía";

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private ContratoRepository contratoRepository;

    /**
     * Estructura interna para ir acumulando por destinatario mientras recorremos
     * pagos y anticipos. Al final se convierte en FilaComisionDTO.
     */
    private static class Acumulador {
        Integer idUsuario;      // null si es Oficina
        String nombre;
        BigDecimal porcentaje;
        BigDecimal montoCobrado = BigDecimal.ZERO;
    }

    /** Acumulador del desglose por modalidad. */
    private static class AcumModalidad {
        int cantidad = 0;
        BigDecimal monto = BigDecimal.ZERO;
    }

    public ReporteComisionesDTO generar(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new IllegalArgumentException("Debes indicar la fecha inicial y la final.");
        }
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la inicial.");
        }

        // Mapa ordenado por inserción: clave = idVendedor de la vendedora, o CLAVE_OFICINA.
        Map<Integer, Acumulador> acumuladores = new LinkedHashMap<>();
        Map<String, AcumModalidad> porModalidad = new LinkedHashMap<>();

        BigDecimal totalEfectivo = BigDecimal.ZERO;
        BigDecimal totalNoEfectivo = BigDecimal.ZERO;
        BigDecimal totalCortesias = BigDecimal.ZERO;
        BigDecimal totalIngresos = BigDecimal.ZERO;
        BigDecimal totalDevoluciones = BigDecimal.ZERO;

        List<MovimientoDetalleDTO> devoluciones = new ArrayList<>();
        List<MovimientoDetalleDTO> cortesias = new ArrayList<>();

        // ---------- 1) PAGOS cobrados en el periodo ----------
        List<Pago> pagos = pagoRepository.findByActivoTrueAndFechaPagoBetween(desde, hasta);
        for (Pago pago : pagos) {
            Contrato contrato = pago.getContrato();
            BigDecimal monto = pago.getMontoPago() != null ? pago.getMontoPago() : BigDecimal.ZERO;
            String modalidad = nombreModalidad(pago.getModoPago());

            // ¿Este movimiento se marcó para que su comisión caiga en Oficina?
            boolean aOficina = pago.esComisionOficina();

            boolean negativo = monto.compareTo(BigDecimal.ZERO) < 0;

            if (negativo) {
                // Es una devolución: se acumula en positivo y se guarda su detalle.
                totalDevoluciones = totalDevoluciones.add(monto.abs());
                devoluciones.add(detalle(
                        pago.getFechaPago(), pago.getFolio(), contrato,
                        monto.abs(), modalidad, pago.getComentarios(), aOficina));
            } else {
                // Todo lo positivo entra a ingresos, cortesías incluidas.
                totalIngresos = totalIngresos.add(monto);
                sumarModalidad(porModalidad, modalidad, monto);
            }

            // Una cortesía no es dinero: no comisiona y va en su propio total.
            if (esCortesia(pago.getModoPago())) {
                totalCortesias = totalCortesias.add(monto);
                cortesias.add(detalle(
                        pago.getFechaPago(), pago.getFolio(), contrato,
                        monto, modalidad, pago.getComentarios(), aOficina));
                continue;
            }

            if (!esCancelado(contrato)) {
                acumular(acumuladores, contrato, monto, aOficina);
            }

            // Efectivo vs no efectivo cuenta SIEMPRE (aunque el contrato esté cancelado,
            // el dinero entró físicamente a la caja ese día). La marca de Oficina NO
            // toca estos totales: solo cambia a quién se le acredita la comisión.
            if (esEfectivo(pago.getModoPago())) {
                totalEfectivo = totalEfectivo.add(monto);
            } else {
                totalNoEfectivo = totalNoEfectivo.add(monto);
            }
        }

        // ---------- 2) ANTICIPOS de contratos firmados en el periodo ----------
        // Ojo: la marca de Oficina vive en el PAGO, y un anticipo no es un pago.
        // Por eso aquí todo sigue exactamente igual que antes.
        List<Contrato> contratos = contratoRepository.findByActivoTrueAndFechaContratoBetween(desde, hasta);
        for (Contrato contrato : contratos) {
            BigDecimal anticipo = contrato.getAnticipo() != null ? contrato.getAnticipo() : BigDecimal.ZERO;
            if (anticipo.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            String modalidad = nombreModalidad(contrato.getModoAnticipo());

            // Un anticipo nunca es negativo: siempre suma a ingresos.
            totalIngresos = totalIngresos.add(anticipo);
            sumarModalidad(porModalidad, modalidad, anticipo);

            // Un anticipo de cortesía tampoco es dinero.
            if (esCortesia(contrato.getModoAnticipo())) {
                totalCortesias = totalCortesias.add(anticipo);
                cortesias.add(detalle(
                        contrato.getFechaContrato(), contrato.getFolio(), contrato,
                        anticipo, modalidad, null));
                continue;
            }

            if (!esCancelado(contrato)) {
                acumular(acumuladores, contrato, anticipo);
            }

            if (esEfectivo(contrato.getModoAnticipo())) {
                totalEfectivo = totalEfectivo.add(anticipo);
            } else {
                totalNoEfectivo = totalNoEfectivo.add(anticipo);
            }
        }

        // ---------- 3) Convertir acumuladores en filas con su comisión ----------
        List<FilaComisionDTO> filas = new ArrayList<>();
        BigDecimal totalCobrado = BigDecimal.ZERO;
        BigDecimal totalComisiones = BigDecimal.ZERO;

        for (Acumulador ac : acumuladores.values()) {
            BigDecimal comision = ac.montoCobrado
                    .multiply(ac.porcentaje)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

            filas.add(new FilaComisionDTO(
                    ac.idUsuario, ac.nombre, ac.porcentaje, ac.montoCobrado, comision));

            totalCobrado = totalCobrado.add(ac.montoCobrado);
            totalComisiones = totalComisiones.add(comision);
        }

        // Los detalles se ordenan por fecha, para que se lean como un diario.
        devoluciones.sort(Comparator.comparing(
                MovimientoDetalleDTO::getFecha,
                Comparator.nullsLast(Comparator.naturalOrder())));
        cortesias.sort(Comparator.comparing(
                MovimientoDetalleDTO::getFecha,
                Comparator.nullsLast(Comparator.naturalOrder())));

        List<TotalPorModalidadDTO> ingresosPorModalidad = new ArrayList<>();
        for (Map.Entry<String, AcumModalidad> e : porModalidad.entrySet()) {
            ingresosPorModalidad.add(new TotalPorModalidadDTO(
                    e.getKey(), e.getValue().cantidad, e.getValue().monto));
        }

        ReporteComisionesDTO reporte = new ReporteComisionesDTO();
        reporte.setDesde(desde);
        reporte.setHasta(hasta);
        reporte.setFilas(filas);
        reporte.setTotalCobrado(totalCobrado);
        reporte.setTotalComisiones(totalComisiones);
        reporte.setTotalEfectivo(totalEfectivo);
        reporte.setTotalNoEfectivo(totalNoEfectivo);
        reporte.setTotalCortesias(totalCortesias);
        reporte.setTotalIngresos(totalIngresos);
        reporte.setTotalDevoluciones(totalDevoluciones);
        reporte.setIngresosPorModalidad(ingresosPorModalidad);
        reporte.setDevoluciones(devoluciones);
        reporte.setCortesias(cortesias);
        return reporte;
    }

    /**
     * Arma un renglón de detalle para las tablas de devoluciones y cortesías.
     * Versión corta: se usa donde no hay marca de Oficina posible (anticipos).
     */
    private MovimientoDetalleDTO detalle(LocalDate fecha,
                                         String folio,
                                         Contrato contrato,
                                         BigDecimal monto,
                                         String modalidad,
                                         String motivo) {
        return detalle(fecha, folio, contrato, monto, modalidad, motivo, false);
    }

    /**
     * Versión larga: recibe si el movimiento se marcó a Oficina, para que en
     * la tabla de detalle aparezca "Oficina" y no el nombre de la vendedora.
     */
    private MovimientoDetalleDTO detalle(LocalDate fecha,
                                         String folio,
                                         Contrato contrato,
                                         BigDecimal monto,
                                         String modalidad,
                                         String motivo,
                                         boolean forzarOficina) {
        MovimientoDetalleDTO d = new MovimientoDetalleDTO();
        d.setFecha(fecha);
        d.setFolio(folio);
        d.setAlumno(nombreAlumno(contrato));
        d.setEscuela(nombreEscuela(contrato));
        d.setMonto(monto);
        d.setModalidad(modalidad);
        d.setMotivo(motivo);

        // forzarOficina hace de cuenta que el contrato no tiene vendedora.
        Vendedor vendedora = forzarOficina ? null : obtenerVendedora(contrato);
        d.setDestinatario(vendedora != null ? vendedora.getNombre() : "Oficina");
        return d;
    }

    /** Suma un monto al desglose por modalidad y cuenta el recibo. */
    private void sumarModalidad(Map<String, AcumModalidad> mapa, String modalidad, BigDecimal monto) {
        AcumModalidad am = mapa.get(modalidad);
        if (am == null) {
            am = new AcumModalidad();
            mapa.put(modalidad, am);
        }
        am.cantidad++;
        am.monto = am.monto.add(monto);
    }

    /**
     * Detalle renglón por renglón del periodo, como el cuerpo de la hoja física:
     * cada cobro con su folio, alumno, escuela y destinatario.
     *
     * RN-04: el anticipo NO lleva folio de recibo; se anota en la columna
     * CONTRATO con el folio del contrato. Los abonos llevan folio de recibo.
     *
     * Las cortesías SÍ aparecen aquí (para que se vean en la hoja del día),
     * pero marcadas como tales y sin destinatario de comisión.
     */
    public List<RenglonCorteDTO> detallar(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new IllegalArgumentException("Debes indicar la fecha inicial y la final.");
        }
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la inicial.");
        }

        List<RenglonCorteDTO> renglones = new ArrayList<>();

        // ---------- ANTICIPOS (van primero, son la firma del contrato) ----------
        List<Contrato> contratos = contratoRepository.findByActivoTrueAndFechaContratoBetween(desde, hasta);
        for (Contrato contrato : contratos) {
            BigDecimal anticipo = contrato.getAnticipo() != null ? contrato.getAnticipo() : BigDecimal.ZERO;
            if (anticipo.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            RenglonCorteDTO r = new RenglonCorteDTO();
            r.setFolioContrato(contrato.getFolio());   // columna CONTRATO
            r.setFolioRecibo(null);                    // el anticipo no lleva recibo
            r.setNombre(nombreAlumno(contrato));
            r.setEscuela(nombreEscuela(contrato));
            r.setMonto(anticipo);
            r.setModalidad(nombreModalidad(contrato.getModoAnticipo()));
            r.setEsEfectivo(esEfectivo(contrato.getModoAnticipo()));
            r.setCancelado(esCancelado(contrato));
            // Un anticipo nunca es una devolución.

            // Una cortesía no se le abona a nadie como comisión.
            if (esCortesia(contrato.getModoAnticipo())) {
                r.setEsCortesia(true);
                r.setIdDestinatario(null);
                r.setNombreDestinatario("Cortesía");
            } else {
                asignarDestinatario(r, contrato);
            }

            renglones.add(r);
        }

        // ---------- PAGOS (los abonos con su folio de recibo) ----------
        List<Pago> pagos = pagoRepository.findByActivoTrueAndFechaPagoBetween(desde, hasta);
        for (Pago pago : pagos) {
            Contrato contrato = pago.getContrato();

            RenglonCorteDTO r = new RenglonCorteDTO();
            r.setFolioContrato(null);
            r.setFolioRecibo(pago.getFolio());         // columna RECIBO
            r.setNombre(nombreAlumno(contrato));
            r.setEscuela(nombreEscuela(contrato));
            r.setMonto(pago.getMontoPago() != null ? pago.getMontoPago() : BigDecimal.ZERO);
            r.setModalidad(nombreModalidad(pago.getModoPago()));
            r.setEsEfectivo(esEfectivo(pago.getModoPago()));
            r.setCancelado(esCancelado(contrato));
            r.setEsDevolucion(pago.esDevolucion());

            if (esCortesia(pago.getModoPago())) {
                r.setEsCortesia(true);
                r.setIdDestinatario(null);
                r.setNombreDestinatario("Cortesía");
            } else {
                // Si el movimiento se marcó a Oficina, la hoja de corte debe
                // decir "Oficina" para que coincida con el reporte de comisiones.
                asignarDestinatario(r, contrato, pago.esComisionOficina());
            }

            renglones.add(r);
        }

        return renglones;
    }

    /** Versión corta: el destinatario sale del contrato, como siempre. */
    private void asignarDestinatario(RenglonCorteDTO renglon, Contrato contrato) {
        asignarDestinatario(renglon, contrato, false);
    }

    /**
     * Versión larga: con forzarOficina en true, el renglón se le acredita a
     * Oficina aunque el contrato traiga vendedora.
     */
    private void asignarDestinatario(RenglonCorteDTO renglon, Contrato contrato, boolean forzarOficina) {
        Vendedor vendedora = forzarOficina ? null : obtenerVendedora(contrato);
        if (vendedora != null) {
            renglon.setIdDestinatario(vendedora.getIdVendedor());
            renglon.setNombreDestinatario(vendedora.getNombre());
        } else {
            renglon.setIdDestinatario(null);
            renglon.setNombreDestinatario("Oficina");
        }
    }

    // El nombre del alumno ahora vive en el propio contrato (nombre_alumno),
    // ya no en la tabla cliente.
    private String nombreAlumno(Contrato contrato) {
        if (contrato == null || contrato.getNombreAlumno() == null
                || contrato.getNombreAlumno().trim().isEmpty()) {
            return "—";
        }
        return contrato.getNombreAlumno();
    }

    /**
     * Columna ESCUELA del papel: siglas + carrera de la O.T.
     * (por ejemplo "ITUG - Biología"), o "ADICIONAL" cuando el contrato
     * no tiene O.T. Escuela y carrera ahora son texto libre en la O.T.
     */
    private String nombreEscuela(Contrato contrato) {
        if (contrato == null) {
            return "—";
        }
        OrdenTrabajo ot = contrato.getOrdenTrabajo();
        if (ot == null) {
            return "ADICIONAL";
        }

        String siglas = ot.getSiglas() != null ? ot.getSiglas().trim() : "";
        String carrera = ot.getCarreraTexto() != null ? ot.getCarreraTexto().trim() : "";

        if (!siglas.isEmpty() && !carrera.isEmpty()) {
            return siglas + " - " + carrera;
        }
        if (!carrera.isEmpty()) {
            return carrera;
        }
        if (!siglas.isEmpty()) {
            return siglas;
        }
        return "O.T. " + ot.getNumero() + "/" + ot.getAnio();
    }

    /**
     * Versión corta: el dinero se le acredita a quien diga el contrato.
     * La usan los anticipos, que no pueden marcarse a Oficina.
     */
    private void acumular(Map<Integer, Acumulador> acumuladores, Contrato contrato, BigDecimal monto) {
        acumular(acumuladores, contrato, monto, false);
    }

    /**
     * Suma un monto al destinatario que corresponde según el contrato.
     * Si el monto viene en negativo (una devolución), aquí se resta solo.
     *
     * forzarOficina en true hace de cuenta que el contrato NO tiene vendedora.
     * No se inventa un camino nuevo: se reusa el que ya existía para los
     * adicionales, que manda el dinero a Oficina con su 10%.
     */
    private void acumular(Map<Integer, Acumulador> acumuladores, Contrato contrato,
                          BigDecimal monto, boolean forzarOficina) {
        Vendedor vendedora = forzarOficina ? null : obtenerVendedora(contrato);

        Integer clave;
        String nombre;
        BigDecimal porcentaje;

        if (vendedora != null) {
            clave = vendedora.getIdVendedor();
            nombre = vendedora.getNombre();
            porcentaje = vendedora.getPorcentajeComision() != null
                    ? vendedora.getPorcentajeComision() : BigDecimal.ZERO;
        } else {
            clave = CLAVE_OFICINA;
            nombre = "Oficina";
            porcentaje = PORCENTAJE_OFICINA;
        }

        Acumulador ac = acumuladores.get(clave);
        if (ac == null) {
            ac = new Acumulador();
            ac.idUsuario = (vendedora != null) ? vendedora.getIdVendedor() : null;
            ac.nombre = nombre;
            ac.porcentaje = porcentaje;
            acumuladores.put(clave, ac);
        }
        ac.montoCobrado = ac.montoCobrado.add(monto);
    }

    /**
     * Devuelve la vendedora del contrato si tiene O.T. con vendedora asignada.
     * Devuelve null si es Adicional (sin O.T.) o si la O.T. no tiene vendedora
     * -> en ambos casos el dinero va a Oficina.
     */
    private Vendedor obtenerVendedora(Contrato contrato) {
        if (contrato == null) return null;
        OrdenTrabajo ot = contrato.getOrdenTrabajo();
        if (ot == null) return null;
        return ot.getVendedor();
    }

    private boolean esCancelado(Contrato contrato) {
        if (contrato == null || contrato.getEstadoContrato() == null) return false;
        String estado = contrato.getEstadoContrato().getNombreEstado();
        return estado != null && estado.toLowerCase().contains("cancel");
    }

    /**
     * Texto de la modalidad para mostrar en la hoja.
     * Si viene vacío se asume Efectivo (así se cargaron los datos históricos).
     */
    private String nombreModalidad(String modo) {
        if (modo == null || modo.trim().isEmpty()) {
            return "Efectivo";
        }
        return modo.trim();
    }

    private boolean esEfectivo(String modo) {
        return modo == null || modo.trim().equalsIgnoreCase("Efectivo");
    }

    /** Una cortesía es un paquete regalado: no hay dinero detrás. */
    private boolean esCortesia(String modo) {
        return modo != null && modo.trim().equalsIgnoreCase(MODO_CORTESIA);
    }
}