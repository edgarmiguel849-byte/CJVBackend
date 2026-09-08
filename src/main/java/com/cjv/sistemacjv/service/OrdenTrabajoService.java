package com.cjv.sistemacjv.service;

import com.cjv.sistemacjv.dto.CeldaMesDTO;
import com.cjv.sistemacjv.dto.FilaMatrizDTO;
import com.cjv.sistemacjv.dto.MatrizOrdenTrabajoDTO;
import com.cjv.sistemacjv.entity.Contrato;
import com.cjv.sistemacjv.entity.OrdenTrabajo;
import com.cjv.sistemacjv.entity.Pago;
import com.cjv.sistemacjv.repository.ContratoRepository;
import com.cjv.sistemacjv.repository.OrdenTrabajoRepository;
import com.cjv.sistemacjv.repository.PagoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class OrdenTrabajoService {
    /** Meses después del CIERRE DE CICLO para marcar un contrato como vencido. */
    private static final int MESES_PARA_VENCER = 4;

    @Autowired
    private OrdenTrabajoRepository ordenTrabajoRepository;

    @Autowired
    private BitacoraLogger bitacoraLogger;

    @Autowired
    private ContratoRepository contratoRepository;

    @Autowired
    private PagoRepository pagoRepository;

    // ===== LISTAR =====

    public List<OrdenTrabajo> listar() {
        return ordenTrabajoRepository.findByActivoTrueOrderByAnioDescNumeroDesc();
    }

    // Ahora la carrera es texto libre dentro de la O.T. (columna carrera_texto).
    public List<OrdenTrabajo> buscarPorCarrera(String texto) {
        return ordenTrabajoRepository
                .findByActivoTrueAndCarreraTextoContainingIgnoreCaseOrderByAnioDescNumeroDesc(texto);
    }

    public Optional<OrdenTrabajo> buscarPorNumeroAnio(Integer numero, Integer anio) {
        return ordenTrabajoRepository.findByNumeroAndAnio(numero, anio);
    }

    // ===== OBTENER UNA =====

    public Optional<OrdenTrabajo> obtenerPorId(Integer id) {
        return ordenTrabajoRepository.findById(id);
    }

    // ===== CREAR =====

    public OrdenTrabajo crear(OrdenTrabajo ot) {
        validarAnio(ot.getAnio());
        validarFechaEntrega(ot.getFechaEntrega());

        Optional<OrdenTrabajo> existente =
                ordenTrabajoRepository.findByNumeroAndAnio(ot.getNumero(), ot.getAnio());
        if (existente.isPresent()) {
            throw new RuntimeException(
                    "Ya existe una O.T. con número " + ot.getNumero() + "/" + ot.getAnio());
        }

        if (ot.getActivo() == null) {
            ot.setActivo(true);
        }

        // Una O.T. nueva nunca nace recorrida: su primera fecha de entrega
        // es la original. El navegador no decide esto.
        ot.setEntregaRecorrida(false);
        ot.setFechaEntregaOriginal(null);

        OrdenTrabajo guardada = ordenTrabajoRepository.save(ot);

        bitacoraLogger.registrar(
                "orden_trabajo",
                guardada.getIdOrdenTrabajo(),
                "CREAR",
                "Se creó la O.T. " + guardada.getNumero() + "/" + guardada.getAnio()
        );

        return guardada;
    }

    // ===== EDITAR =====

    public OrdenTrabajo editar(Integer id, OrdenTrabajo datos) {
        OrdenTrabajo actual = ordenTrabajoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("O.T. no encontrada con id " + id));

        validarAnio(datos.getAnio());
        validarFechaEntrega(datos.getFechaEntrega());

        boolean cambioNumero = !actual.getNumero().equals(datos.getNumero());
        boolean cambioAnio = !actual.getAnio().equals(datos.getAnio());
        if (cambioNumero || cambioAnio) {
            Optional<OrdenTrabajo> existente =
                    ordenTrabajoRepository.findByNumeroAndAnio(datos.getNumero(), datos.getAnio());
            if (existente.isPresent()
                    && !existente.get().getIdOrdenTrabajo().equals(id)) {
                throw new RuntimeException(
                        "Ya existe otra O.T. con número " + datos.getNumero() + "/" + datos.getAnio());
            }
        }

        actual.setNumero(datos.getNumero());
        actual.setAnio(datos.getAnio());
        actual.setEscuela(datos.getEscuela());
        actual.setSiglas(datos.getSiglas());
        actual.setCarreraTexto(datos.getCarreraTexto());
        actual.setModalidad(datos.getModalidad());
        actual.setDiasCobro(datos.getDiasCobro());
        actual.setGrupo(datos.getGrupo());
        actual.setGeneracion(datos.getGeneracion());
        actual.setVendedor(datos.getVendedor());

        // ===== RECORRIMIENTO DE LA ENTREGA =====
        //
        // Recorrer = mover una fecha de entrega ya comprometida con la
        // escuela. Lo detecta el SERVIDOR comparando, no el navegador.
        //
        // Fíjate que NO se copian datos.getEntregaRecorrida() ni
        // datos.getFechaEntregaOriginal(): aunque el JSON las traiga, se
        // ignoran a propósito. Si se aceptaran, alguien podría prender el
        // rojo sin haber movido nada, o mover la fecha sin que se note.
        // Es la misma idea del candado de fechas en Pagos: la cortina
        // está en Angular, la chapa vive aquí.
        LocalDate fechaAnterior = actual.getFechaEntrega();
        LocalDate fechaNueva = datos.getFechaEntrega();

        // Llenar una fecha que estaba VACÍA no es recorrer: es capturar un
        // dato que faltaba. Solo hay recorrimiento si había un compromiso
        // previo y se movió.
        // Recorrer es EMPUJAR la fecha hacia adelante. Adelantarla no es
        // recorrer: no rompe ningún compromiso, la escuela recibe antes.
        boolean seRecorrio = fechaAnterior != null
                && fechaNueva != null
                && fechaNueva.isAfter(fechaAnterior);

        // Solo para la bitácora. NO apaga la bandera: si una entrega ya
        // recorrida luego se adelanta, se queda prendida. El compromiso
        // original se rompió igual.
        boolean seAdelanto = fechaAnterior != null
                && fechaNueva != null
                && fechaNueva.isBefore(fechaAnterior);

        if (seRecorrio) {
            // La original se escribe UNA sola vez. Si el grupo se recorre
            // tres veces, "original" tiene que seguir siendo la primera:
            // es la que trae la escuela en el papel cuando reclama.
            if (actual.getFechaEntregaOriginal() == null) {
                actual.setFechaEntregaOriginal(fechaAnterior);
            }
            actual.setEntregaRecorrida(true);
        }

        actual.setFechaEntrega(fechaNueva);
        // =======================================

        actual.setFechaCierreCiclo(datos.getFechaCierreCiclo());

        OrdenTrabajo actualizada = ordenTrabajoRepository.save(actual);

        bitacoraLogger.registrar(
                "orden_trabajo",
                actualizada.getIdOrdenTrabajo(),
                "EDITAR",
                "Se editó la O.T. " + actualizada.getNumero() + "/" + actualizada.getAnio()
                        + (seRecorrio
                        ? " - Entrega RECORRIDA: era " + fechaAnterior
                          + ", ahora " + fechaNueva
                        : "")
        );

        return actualizada;
    }

    // ===== ELIMINAR (lógico) =====

    public void eliminar(Integer id) {
        OrdenTrabajo ot = ordenTrabajoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("O.T. no encontrada con id " + id));

        ot.setActivo(false);
        ordenTrabajoRepository.save(ot);

        bitacoraLogger.registrar(
                "orden_trabajo",
                ot.getIdOrdenTrabajo(),
                "ELIMINAR",
                "Se eliminó (lógico) la O.T. " + ot.getNumero() + "/" + ot.getAnio()
        );
    }

    // ===== VALIDACIONES =====

    /**
     * La fecha de entrega es obligatoria: es el compromiso con la escuela
     * y es de donde cuelga el recorrimiento.
     *
     * La columna ya es NOT NULL en la base, así que sin esta validación el
     * usuario vería un 500 sin explicación. Esto la convierte en un 400 con
     * mensaje legible: el controlador atrapa RuntimeException y devuelve
     * su texto tal cual.
     */
    private void validarFechaEntrega(LocalDate fechaEntrega) {
        if (fechaEntrega == null) {
            throw new RuntimeException(
                    "La fecha de entrega es obligatoria.");
        }
    }

    private void validarAnio(Integer anio) {
        if (anio == null || anio < 2000 || anio > 2100) {
            throw new RuntimeException(
                    "El año debe guardarse completo, con 4 dígitos (por ejemplo 2026).");
        }
    }

    // ===== MATRIZ DE LA O.T. =====

    public MatrizOrdenTrabajoDTO generarMatriz(Integer idOrdenTrabajo) {
        OrdenTrabajo ot = ordenTrabajoRepository.findById(idOrdenTrabajo)
                .orElseThrow(() -> new RuntimeException("O.T. no encontrada con id " + idOrdenTrabajo));

        List<Contrato> contratos = contratoRepository
                .findByOrdenTrabajo_IdOrdenTrabajoOrderByNumeroListaAsc(idOrdenTrabajo);

        YearMonth primerMes = detectarPrimerMes(contratos);

        List<YearMonth> mesesYM = new ArrayList<>();
        List<String> mesesEtiqueta = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            YearMonth ym = primerMes.plusMonths(i);
            mesesYM.add(ym);
            mesesEtiqueta.add(etiquetaMes(ym));
        }

        BigDecimal totalAnticipos = BigDecimal.ZERO;
        List<BigDecimal> totalesPorMes = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            totalesPorMes.add(BigDecimal.ZERO);
        }
        BigDecimal totalGeneral = BigDecimal.ZERO;
        BigDecimal abonadoGeneral = BigDecimal.ZERO;
        BigDecimal restaGeneral = BigDecimal.ZERO;

        List<FilaMatrizDTO> filas = new ArrayList<>();

        for (Contrato c : contratos) {
            boolean cancelado = esCancelado(c);

            List<Pago> pagos = pagoRepository
                    .findByContrato_IdContratoAndActivoTrue(c.getIdContrato());

            List<List<CeldaMesDTO>> celdasPorMes = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                celdasPorMes.add(new ArrayList<>());
            }

            BigDecimal sumaPagos = BigDecimal.ZERO;

            for (Pago p : pagos) {
                if (p.getFechaPago() == null) continue;
                YearMonth mesPago = YearMonth.from(p.getFechaPago());
                int indice = mesesYM.indexOf(mesPago);
                if (indice >= 0) {
                    // La marca de Oficina viaja PAGO POR PAGO, no por celda:
                    // un alumno puede tener dos pagos el mismo mes con uno
                    // marcado y el otro no.
                    // "Cortesía" se compara contra la constante de
                    // ComisionService a propósito: la palabra lleva ACENTO
                    // y si se teclea aquí sin él, nunca coincide y el dorado
                    // simplemente no aparece, sin ningún error que lo delate.
                    String modo = p.getModoPago();
                    boolean esCortesia = modo != null
                            && modo.trim().equalsIgnoreCase(ComisionService.MODO_CORTESIA);

                    celdasPorMes.get(indice).add(
                            new CeldaMesDTO(
                                    p.getFolio(),
                                    p.getMontoPago(),
                                    p.esComisionOficina(),
                                    esCortesia));
                }
                sumaPagos = sumaPagos.add(p.getMontoPago());

                if (!cancelado && indice >= 0) {
                    totalesPorMes.set(indice,
                            totalesPorMes.get(indice).add(p.getMontoPago()));
                }
            }

            // Con el heredado: en la matriz importa cuánto lleva pagado el
            // alumno, no cuánto entró a caja. Un recontratado arrastra saldo.
            BigDecimal anticipo = c.getAbonadoInicial();
            BigDecimal total = c.getTotal() != null ? c.getTotal() : BigDecimal.ZERO;
            BigDecimal abonado = anticipo.add(sumaPagos);
            BigDecimal resta = total.subtract(abonado);

            // El nombre del alumno ahora vive en el contrato (nombre_alumno),
            // ya no en la tabla cliente. La matriz lo pone completo en la
            // primera columna; las de apellido quedan vacías.
            String nombre = valor(c.getNombreAlumno());
            String apPat = "";
            String apMat = "";

            filas.add(new FilaMatrizDTO(
                    c.getNumeroLista(),
                    c.getFolio(),
                    nombre, apPat, apMat,
                    anticipo,
                    celdasPorMes,
                    total, abonado, resta,
                    cancelado,
                    esVencido(ot, resta, cancelado),
                    c.getTraspasadoA()
            ));

            if (!cancelado) {
                totalAnticipos = totalAnticipos.add(anticipo);
                totalGeneral = totalGeneral.add(total);
                abonadoGeneral = abonadoGeneral.add(abonado);
                restaGeneral = restaGeneral.add(resta);
            }
        }

        return new MatrizOrdenTrabajoDTO(
                ot, mesesEtiqueta, filas,
                totalAnticipos, totalesPorMes,
                totalGeneral, abonadoGeneral, restaGeneral
        );
    }

    private YearMonth detectarPrimerMes(List<Contrato> contratos) {
        YearMonth minimo = null;

        for (Contrato c : contratos) {
            List<Pago> pagos = pagoRepository
                    .findByContrato_IdContratoAndActivoTrue(c.getIdContrato());
            for (Pago p : pagos) {
                if (p.getFechaPago() != null) {
                    YearMonth ym = YearMonth.from(p.getFechaPago());
                    if (minimo == null || ym.isBefore(minimo)) {
                        minimo = ym;
                    }
                }
            }
        }

        if (minimo == null) {
            for (Contrato c : contratos) {
                if (c.getFechaContrato() != null) {
                    YearMonth ym = YearMonth.from(c.getFechaContrato());
                    if (minimo == null || ym.isBefore(minimo)) {
                        minimo = ym;
                    }
                }
            }
        }

        return minimo != null ? minimo : YearMonth.now();
    }

    private String etiquetaMes(YearMonth ym) {
        String nombreMes = ym.getMonth()
                .getDisplayName(TextStyle.FULL, new Locale("es", "ES"));
        nombreMes = nombreMes.substring(0, 1).toUpperCase() + nombreMes.substring(1);
        return nombreMes + " " + ym.getYear();
    }

    /**
     * ¿Este contrato de grupo está vencido?
     *
     * Regla: pasaron 4 meses desde el CIERRE DE CICLO de su O.T. y el alumno
     * todavía debe dinero. Sin cierre de ciclo capturado no vence nunca
     * (sin fecha no hay reloj). Un cancelado no se marca vencido: manda la
     * decisión humana.
     *
     * OJO: es SOLO una etiqueta informativa. No cambia el estado del contrato
     * en la base ni afecta comisiones, entregas ni cobros.
     */
    private boolean esVencido(OrdenTrabajo ot, BigDecimal resta, boolean cancelado) {
        if (cancelado) {
            return false;
        }
        if (resta == null || resta.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (ot == null || ot.getFechaCierreCiclo() == null) {
            return false;
        }
        return LocalDate.now().isAfter(
                ot.getFechaCierreCiclo().plusMonths(MESES_PARA_VENCER));
    }

    private boolean esCancelado(Contrato c) {
        if (c.getEstadoContrato() == null) return false;
        String estado = c.getEstadoContrato().getNombreEstado();
        return estado != null && estado.toLowerCase().contains("cancel");
    }

    private String valor(String s) {
        return s != null ? s : "";
    }
}