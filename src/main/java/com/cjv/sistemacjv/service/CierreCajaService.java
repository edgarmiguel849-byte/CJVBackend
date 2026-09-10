package com.cjv.sistemacjv.service;

import com.cjv.sistemacjv.dto.CorteDePersonaDTO;
import com.cjv.sistemacjv.dto.EstadoDelDiaDTO;
import com.cjv.sistemacjv.entity.CierreCaja;
import com.cjv.sistemacjv.entity.CierreCajaDetalle;
import com.cjv.sistemacjv.entity.Usuario;
import com.cjv.sistemacjv.repository.CierreCajaDetalleRepository;
import com.cjv.sistemacjv.repository.CierreCajaRepository;
import com.cjv.sistemacjv.repository.ContratoRepository;
import com.cjv.sistemacjv.repository.EgresoRepository;
import com.cjv.sistemacjv.repository.PagoRepository;
import com.cjv.sistemacjv.repository.UsuarioRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * El corte de caja: cada persona cuenta SU efectivo y entrega SU corte;
 * el jefe revisa y autoriza. El jefe también puede devolverlo para
 * corregir (REABIERTO), y entonces se reenvía.
 *
 * QUIÉN ENTREGA CORTE:
 *   MOSTRADOR     -> uno por día.
 *   ADMINISTRADOR -> los que necesite, por el volumen de folios.
 *   JEFE          -> ninguno. Solo revisa y autoriza.
 *
 * CADA CORTE SE LLEVA lo que ESA persona capturó ese día y que todavía no
 * viaja en ningún corte. Eso último es lo que hace posible que el segundo
 * corte del día no vuelva a barrer lo del primero: al entregar se escribe
 * un renglón en cierre_caja_detalle por cada movimiento, y al calcular se
 * saltan los que ya tienen renglón.
 *
 * Los números se guardan CONGELADOS: son el acta de lo que se contó. Si
 * después alguien edita un pago, el acta no cambia — y esa diferencia
 * entre el acta y el recálculo es justo la pista de que algo se movió.
 *
 * LOS TRES ESTADOS:
 *   ENVIADO    -> la persona entregó su corte. Sus movimientos quedan congelados.
 *   AUTORIZADO -> el jefe lo revisó y lo firmó. Siguen congelados.
 *   REABIERTO  -> el jefe lo devolvió para corregir. Sus movimientos se
 *                 SUELTAN para que se arreglen y se reenvíe.
 */
@Service
public class CierreCajaService {

    public static final String ESTADO_ENVIADO = "ENVIADO";
    public static final String ESTADO_AUTORIZADO = "AUTORIZADO";
    public static final String ESTADO_REABIERTO = "REABIERTO";

    // Nombres tal como están escritos en la tabla 'rol'.
    private static final String ROL_JEFE = "Jefe";
    private static final String ROL_ADMINISTRADOR = "Administrador";
    private static final String ROL_MOSTRADOR = "Mostrador";

    /**
     * LA FRONTERA. Antes de esta fecha manda la regla VIEJA (un movimiento
     * está congelado si su FECHA tiene corte entregado); de aquí en
     * adelante manda la NUEVA (está congelado si tiene renglón en
     * cierre_caja_detalle).
     *
     * Existe porque cierre_caja_detalle nació vacía: los movimientos
     * históricos no tienen renglón, y sin esta frontera todos ellos
     * quedarían descongelados de golpe el día que se prenda la regla nueva.
     * Entre ellos hay cobros reales de alumnos.
     *
     * NO la muevas hacia atrás. Adelantarla solo tiene sentido si algún día
     * se rellena el detalle hacia atrás.
     */
    public static final LocalDate INICIO_CORTES_POR_PERSONA =
            LocalDate.of(2026, 9, 9);

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Del más VIEJO al más NUEVO dentro de un mismo día.
     *
     * Ordena por fecha_hora_entrega, y cuando esa hora es null cae al id.
     * El null aparece en los cortes anteriores a que existiera la columna:
     * no se sabe su hora y no se inventa, así que para ellos el
     * autoincremento es la única pista de orden que hay. Se mandan al
     * principio a propósito: son los más viejos.
     */
    private static final Comparator<CierreCaja> POR_MOMENTO_DE_ENTREGA =
            Comparator
                    .comparing(CierreCaja::getFechaHoraEntrega,
                            Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparing(CierreCaja::getIdCierreCaja,
                            Comparator.nullsFirst(Comparator.naturalOrder()));

    private final CierreCajaRepository cierreCajaRepository;
    private final CierreCajaDetalleRepository detalleRepository;
    private final UsuarioRepository usuarioRepository;
    private final PagoRepository pagoRepository;
    private final ContratoRepository contratoRepository;
    private final EgresoRepository egresoRepository;
    private final ComisionService comisionService;
    private final BitacoraLogger bitacoraLogger;

    public CierreCajaService(CierreCajaRepository cierreCajaRepository,
                             CierreCajaDetalleRepository detalleRepository,
                             UsuarioRepository usuarioRepository,
                             PagoRepository pagoRepository,
                             ContratoRepository contratoRepository,
                             EgresoRepository egresoRepository,
                             ComisionService comisionService,
                             BitacoraLogger bitacoraLogger) {
        this.cierreCajaRepository = cierreCajaRepository;
        this.detalleRepository = detalleRepository;
        this.usuarioRepository = usuarioRepository;
        this.pagoRepository = pagoRepository;
        this.contratoRepository = contratoRepository;
        this.egresoRepository = egresoRepository;
        this.comisionService = comisionService;
        this.bitacoraLogger = bitacoraLogger;
    }

    public List<CierreCaja> listarCierresCaja() {
        return cierreCajaRepository.findAll();
    }

    public Optional<CierreCaja> buscarPorId(Integer id) {
        return cierreCajaRepository.findById(id);
    }

    /**
     * TODOS los cortes de un día. Es la consulta buena ahora que un día
     * puede tener varios.
     *
     * Salen del más viejo al más nuevo por hora de entrega. Es el orden en
     * que el Jefe los va a ver en su lista, y el orden en que ocurrieron.
     */
    public List<CierreCaja> listarPorFecha(LocalDate fecha) {
        return cierreCajaRepository
                .findAllByFechaCierreOrderByIdCierreCajaAsc(fecha)
                .stream()
                .sorted(POR_MOMENTO_DE_ENTREGA)
                .toList();
    }

    /**
     * PROVISIONAL: un corte suelto del día, para que sobreviva la pantalla
     * vieja mientras se rehace.
     *
     * Devuelve el MÁS RECIENTE, no el primero. Antes daba el primero, que
     * con dos cortes en un día es el equivocado: el dinero que está en el
     * cajón ahorita corresponde al último, no al de la mañana.
     *
     * Aun así sigue siendo mentira cuando hay varios, porque enseña UNO
     * como si fuera "el corte del día". La pantalla del Jefe usa
     * listarPorFecha() y los muestra todos.
     */
    public Optional<CierreCaja> buscarPorFecha(LocalDate fecha) {
        List<CierreCaja> delDia = listarPorFecha(fecha);
        return delDia.isEmpty()
                ? Optional.empty()
                : Optional.of(delDia.get(delDia.size() - 1));
    }

    /**
     * El ÚLTIMO corte entregado de esta persona en este día, con sus
     * cifras congeladas.
     *
     * Es lo que necesita la pantalla de quien entrega: si el Administrador
     * entregó a mediodía y otra vez en la tarde, el acta que le toca ver
     * al recargar es la de la tarde.
     *
     * Un corte REABIERTO no cuenta como entregado: está devuelto para
     * corregirse, así que quien pregunte debe recibir vacío y volver al
     * modo de captura.
     */
    public Optional<CierreCaja> buscarUltimoDePersona(LocalDate fecha, Integer idUsuario) {
        return cierreCajaRepository
                .findAllByFechaCierreAndUsuario_IdUsuarioOrderByIdCierreCajaAsc(fecha, idUsuario)
                .stream()
                .filter(c -> estadoTrabaElDia(c.getEstado()))
                .max(POR_MOMENTO_DE_ENTREGA);
    }

    /**
     * EL SEMÁFORO DEL MES para el calendario del Jefe.
     *
     * Un renglón por día CON cortes. Los días sin ningún corte NO vienen:
     * el navegador los pinta grises por ausencia, y así no viaja un mes
     * lleno de renglones vacíos.
     *
     * La regla del color vive AQUÍ, no en el navegador, para que exista
     * escrita en un solo lugar:
     *   VERDE    -> todos autorizados.
     *   AMARILLO -> unos sí y otros no, o hay alguno REABIERTO.
     *   ROJO     -> hay cortes y ninguno autorizado.
     *
     * OJO CON LO QUE NO DICE: si alguien FALTÓ por entregar. El sistema no
     * sabe quién trabajó cada día, así que un día con dos cortes se ve
     * igual hayan trabajado dos personas o tres. Se decidió así para no
     * inventar un módulo de asistencia.
     */
    public List<EstadoDelDiaDTO> estadosDelMes(int anio, int mes) {
        if (mes < 1 || mes > 12) {
            throw new IllegalArgumentException(
                    "El mes debe ir del 1 al 12. Llegó: " + mes);
        }
        if (anio < 2000 || anio > 2200) {
            throw new IllegalArgumentException(
                    "Año fuera de rango: " + anio);
        }

        YearMonth deQueMes = YearMonth.of(anio, mes);
        LocalDate primero = deQueMes.atDay(1);
        LocalDate ultimo = deQueMes.atEndOfMonth();

        // Todos los cortes del mes en UNA consulta. Pedir día por día
        // serían 30 viajes a la base para pintar una pantalla.
        Map<LocalDate, List<CierreCaja>> porDia = cierreCajaRepository
                .findAllByFechaCierreBetweenOrderByFechaCierreAsc(primero, ultimo)
                .stream()
                .collect(Collectors.groupingBy(CierreCaja::getFechaCierre));

        List<EstadoDelDiaDTO> estados = new ArrayList<>();

        for (Map.Entry<LocalDate, List<CierreCaja>> dia : porDia.entrySet()) {
            List<CierreCaja> cortes = dia.getValue();

            int total = cortes.size();

            int autorizados = (int) cortes.stream()
                    .filter(c -> ESTADO_AUTORIZADO.equals(c.getEstado()))
                    .count();

            boolean hayReabiertos = cortes.stream()
                    .anyMatch(c -> ESTADO_REABIERTO.equals(c.getEstado()));

            String color;
            if (hayReabiertos) {
                // Un corte devuelto para corregir es trabajo pendiente,
                // aunque todos los demás del día ya estén firmados.
                color = EstadoDelDiaDTO.AMARILLO;
            } else if (autorizados == total) {
                color = EstadoDelDiaDTO.VERDE;
            } else if (autorizados == 0) {
                color = EstadoDelDiaDTO.ROJO;
            } else {
                color = EstadoDelDiaDTO.AMARILLO;
            }

            estados.add(new EstadoDelDiaDTO(
                    dia.getKey(), color, total, autorizados, hayReabiertos));
        }

        estados.sort(Comparator.comparing(EstadoDelDiaDTO::getFecha));
        return estados;
    }

    /** Historial completo, del más reciente al más viejo. */
    public List<CierreCaja> listarHistorial() {
        return cierreCajaRepository.findAllByOrderByFechaCierreDesc();
    }

    /**
     * ¿Esta persona ya entregó corte de este día y sigue entregado?
     *
     * Un corte REABIERTO no cuenta: está devuelto justo para que se
     * corrija y se reenvíe.
     */
    public boolean yaEntregoSuCorte(LocalDate fecha, Integer idUsuario) {
        return cierreCajaRepository
                .findAllByFechaCierreAndUsuario_IdUsuarioOrderByIdCierreCajaAsc(fecha, idUsuario)
                .stream()
                .anyMatch(c -> estadoTrabaElDia(c.getEstado()));
    }

    /**
     * ¿Ese estado de cierre traba?
     *
     * Traba = NO está reabierto. O sea: ENVIADO y AUTORIZADO congelan;
     * REABIERTO suelta.
     *
     * Vive aquí, static, para que la regla esté escrita en UN SOLO lugar
     * y no se repita mal copiada en PagoService, ContratoService y
     * EgresoService, que son los tres que la consultan.
     */
    public static boolean estadoTrabaElDia(String estadoDelCierre) {
        if (estadoDelCierre == null) {
            // Un cierre sin estado es raro; por seguridad se considera trabado.
            return true;
        }
        return !ESTADO_REABIERTO.equalsIgnoreCase(estadoDelCierre.trim());
    }

    /**
     * La persona cuenta SU efectivo y entrega SU corte.
     *
     * Se lleva lo que ELLA capturó ese día y que no viaja ya en otro
     * corte. El servidor recalcula todo: nunca se confía en el navegador
     * para números de dinero.
     *
     * Si esa persona tenía un corte REABIERTO de ese día, este envío es la
     * CORRECCIÓN de aquel: se reusa la misma fila, se tiran sus renglones
     * viejos y se vuelven a escribir con lo que quedó.
     *
     * @Transactional porque son dos escrituras — el cierre y sus renglones.
     * Si falla la segunda, se deshace la primera; si no, quedaría un corte
     * entregado sin renglones, o sea dinero congelado que nadie marcó.
     */
    @Transactional
    public CierreCaja enviar(LocalDate fecha,
                             BigDecimal efectivoContado,
                             String comentarios) {

        if (fecha == null) {
            throw new IllegalArgumentException("Debes indicar la fecha del cierre.");
        }
        if (fecha.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "No puedes cerrar un día que todavía no llega.");
        }
        if (efectivoContado == null || efectivoContado.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Captura cuánto efectivo contaste. No puede ser negativo.");
        }

        Usuario quienEnvia = obtenerUsuarioLogueado();
        String rol = nombreRol(quienEnvia);

        // ---------- CANDADO DE ROL ----------
        // Antes NO había ninguno: cualquiera con sesión podía mandar corte.
        if (ROL_JEFE.equalsIgnoreCase(rol)) {
            throw new IllegalArgumentException(
                    "El jefe no entrega corte de caja: revisa y autoriza los "
                            + "de los demás.");
        }
        if (!ROL_MOSTRADOR.equalsIgnoreCase(rol)
                && !ROL_ADMINISTRADOR.equalsIgnoreCase(rol)) {
            throw new IllegalArgumentException(
                    "Tu rol no tiene permiso para entregar cortes de caja.");
        }

        // ---------- ¿ES CORRECCIÓN O ES NUEVO? ----------
        List<CierreCaja> mios = cierreCajaRepository
                .findAllByFechaCierreAndUsuario_IdUsuarioOrderByIdCierreCajaAsc(
                        fecha, quienEnvia.getIdUsuario());

        // Un corte reabierto MÍO de ese día es el que hay que corregir.
        Optional<CierreCaja> reabierto = mios.stream()
                .filter(c -> ESTADO_REABIERTO.equals(c.getEstado()))
                .findFirst();

        boolean esReenvio = reabierto.isPresent();

        // El Mostrador entrega UNO al día. El Administrador, los que necesite.
        if (!esReenvio && ROL_MOSTRADOR.equalsIgnoreCase(rol)) {
            boolean yaEntrego = mios.stream()
                    .anyMatch(c -> estadoTrabaElDia(c.getEstado()));
            if (yaEntrego) {
                throw new IllegalArgumentException(
                        "Ya entregaste tu corte del " + fecha.format(FORMATO_FECHA)
                                + ". Si hay que corregirlo, pídele al jefe que lo reabra.");
            }
        }

        // ---------- LO QUE SE VA A LLEVAR ----------
        CorteDePersonaDTO corte = comisionService.generarCorteDePersona(
                fecha, quienEnvia.getIdUsuario(), quienEnvia.getNombreUsuario());

        if (corte.estaVacio()) {
            throw new IllegalArgumentException(
                    "No hay movimientos tuyos por cortar el "
                            + fecha.format(FORMATO_FECHA) + ".");
        }

        BigDecimal esperado = valorSeguro(corte.getEfectivoEnCaja());
        BigDecimal ingresos = valorSeguro(corte.getTotalIngresos());
        BigDecimal egresos = valorSeguro(corte.getTotalEgresos());

        BigDecimal diferencia = efectivoContado.subtract(esperado);

        // Si no cuadra, hay que explicar por qué.
        String comentarioLimpio = comentarios == null ? "" : comentarios.trim();
        if (diferencia.compareTo(BigDecimal.ZERO) != 0 && comentarioLimpio.isEmpty()) {
            String falta = diferencia.compareTo(BigDecimal.ZERO) < 0 ? "Faltan" : "Sobran";
            throw new IllegalArgumentException(
                    falta + " $" + diferencia.abs() + " en la caja. "
                            + "Escribe un comentario explicando la diferencia antes de enviar.");
        }

        // Si es corrección se reusa la fila que ya existe; si no, una nueva.
        CierreCaja cierre = reabierto.orElseGet(CierreCaja::new);

        cierre.setFechaCierre(fecha);
        cierre.setUsuario(quienEnvia);
        cierre.setEfectivoEsperado(esperado);
        cierre.setEfectivoContado(efectivoContado);
        cierre.setDiferencia(diferencia);
        cierre.setTotalIngresos(ingresos);
        cierre.setTotalEgresos(egresos);
        cierre.setTotalCierre(esperado);
        cierre.setComentarios(comentarioLimpio.isEmpty() ? null : comentarioLimpio);
        cierre.setEstado(ESTADO_ENVIADO);
        cierre.setActivo(true);

        // El momento en que se entregó. OJO: fecha es el DÍA que se está
        // cortando y puede ser de ayer; esto es el reloj de AHORA, cuando
        // se contó el dinero. Son cosas distintas a propósito.
        //
        // En una corrección se pisa la hora vieja: el acta que vale es la
        // corregida, y su hora es la de este reenvío.
        cierre.setFechaHoraEntrega(LocalDateTime.now());

        // Al reenviar, la firma vieja del jefe ya no aplica: se limpia.
        cierre.setUsuarioAutoriza(null);
        cierre.setFechaAutorizacion(null);

        CierreCaja guardado = cierreCajaRepository.save(cierre);

        // ---------- LOS RENGLONES: qué se llevó este corte ----------
        // En una corrección se tiran los viejos primero. Si no, un pago que
        // se borró durante la corrección seguiría marcado como cortado.
        if (esReenvio) {
            detalleRepository.deleteByCierreCaja_IdCierreCaja(guardado.getIdCierreCaja());
        }

        for (Integer idPago : corte.getIdsPagos()) {
            CierreCajaDetalle d = new CierreCajaDetalle();
            d.setCierreCaja(guardado);
            d.setPago(pagoRepository.getReferenceById(idPago));
            d.setActivo(true);
            detalleRepository.save(d);
        }
        for (Integer idContrato : corte.getIdsContratos()) {
            CierreCajaDetalle d = new CierreCajaDetalle();
            d.setCierreCaja(guardado);
            d.setContrato(contratoRepository.getReferenceById(idContrato));
            d.setActivo(true);
            detalleRepository.save(d);
        }
        for (Integer idEgreso : corte.getIdsEgresos()) {
            CierreCajaDetalle d = new CierreCajaDetalle();
            d.setCierreCaja(guardado);
            d.setEgreso(egresoRepository.getReferenceById(idEgreso));
            d.setActivo(true);
            detalleRepository.save(d);
        }

        int cuantos = corte.getIdsPagos().size()
                + corte.getIdsContratos().size()
                + corte.getIdsEgresos().size();

        bitacoraLogger.registrar(
                "cierre_caja",
                guardado.getIdCierreCaja(),
                esReenvio ? "REENVIAR" : "CREAR",
                "Corte del " + fecha.format(FORMATO_FECHA)
                        + (esReenvio ? " REENVIADO (corregido) por " : " entregado por ")
                        + quienEnvia.getNombreUsuario()
                        + " - " + cuantos + " movimientos"
                        + " - Esperado: $" + esperado
                        + " - Contado: $" + efectivoContado
                        + " - Diferencia: $" + diferencia
        );

        return guardado;
    }

    /**
     * El jefe revisa y autoriza. Solo el rol Jefe puede.
     * No se puede autorizar un cierre REABIERTO: primero hay que
     * corregirlo y reenviarlo, porque si no se estaría firmando el acta
     * que justamente se mandó a corregir.
     */
    public CierreCaja autorizar(Integer idCierre) {
        Usuario quienAutoriza = obtenerUsuarioLogueado();
        exigirRolJefe(quienAutoriza, "autorizar cierres de caja");

        CierreCaja cierre = cierreCajaRepository.findById(idCierre)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el cierre con ID " + idCierre));

        if (ESTADO_AUTORIZADO.equals(cierre.getEstado())) {
            throw new IllegalArgumentException(
                    "Ese cierre ya estaba autorizado.");
        }

        if (ESTADO_REABIERTO.equals(cierre.getEstado())) {
            throw new IllegalArgumentException(
                    "Ese cierre está reabierto y espera correcciones. "
                            + "Debe reenviarse antes de que puedas autorizarlo.");
        }

        cierre.setEstado(ESTADO_AUTORIZADO);
        cierre.setUsuarioAutoriza(quienAutoriza);
        cierre.setFechaAutorizacion(LocalDateTime.now());

        CierreCaja autorizado = cierreCajaRepository.save(cierre);

        bitacoraLogger.registrar(
                "cierre_caja",
                autorizado.getIdCierreCaja(),
                "AUTORIZAR",
                "Corte del " + autorizado.getFechaCierre().format(FORMATO_FECHA)
                        + " de " + nombreDe(autorizado)
                        + " autorizado por " + quienAutoriza.getNombreUsuario()
        );

        return autorizado;
    }

    /**
     * El jefe devuelve el corte para que se corrija.
     *
     * Sirve tanto para uno ya AUTORIZADO como para uno apenas ENVIADO (si
     * el jefe ve el error antes de firmar, no tiene caso obligarlo a
     * firmar primero).
     *
     * REABIERTO es el único estado que SUELTA los movimientos de ese corte:
     * los renglones de cierre_caja_detalle siguen ahí, pero las consultas
     * los ignoran mientras el corte esté reabierto. Por eso se pueden
     * volver a editar y reenviar.
     */
    public CierreCaja reabrir(Integer idCierre) {
        Usuario quienReabre = obtenerUsuarioLogueado();
        exigirRolJefe(quienReabre, "reabrir cierres de caja");

        CierreCaja cierre = cierreCajaRepository.findById(idCierre)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el cierre con ID " + idCierre));

        if (ESTADO_REABIERTO.equals(cierre.getEstado())) {
            throw new IllegalArgumentException(
                    "Ese cierre ya está reabierto, esperando que lo corrijan y reenvíen.");
        }

        cierre.setEstado(ESTADO_REABIERTO);
        cierre.setUsuarioAutoriza(null);
        cierre.setFechaAutorizacion(null);

        CierreCaja reabierto = cierreCajaRepository.save(cierre);

        bitacoraLogger.registrar(
                "cierre_caja",
                reabierto.getIdCierreCaja(),
                "REABRIR",
                "Corte del " + reabierto.getFechaCierre().format(FORMATO_FECHA)
                        + " de " + nombreDe(reabierto)
                        + " reabierto para corrección por " + quienReabre.getNombreUsuario()
        );

        return reabierto;
    }

    /**
     * Borra un corte para que se pueda rehacer desde cero.
     * Solo el jefe, y solo si NO está autorizado (primero hay que reabrirlo).
     *
     * OJO CON EL ORDEN: los renglones se borran ANTES que el corte. La
     * llave foránea fk_detalle_cierre_caja es ON DELETE RESTRICT, así que
     * MySQL se niega a borrar un cierre que todavía tenga renglones y el
     * error que sale es ilegible.
     *
     * Con el flujo de REABIERTO ya casi no hace falta borrar, pero se deja
     * como salida de emergencia.
     */
    @Transactional
    public boolean eliminarCierreCaja(Integer id) {
        Usuario quienBorra = obtenerUsuarioLogueado();
        exigirRolJefe(quienBorra, "eliminar cierres de caja");

        Optional<CierreCaja> encontrado = cierreCajaRepository.findById(id);
        if (encontrado.isEmpty()) {
            return false;
        }

        CierreCaja cierre = encontrado.get();

        if (ESTADO_AUTORIZADO.equals(cierre.getEstado())) {
            throw new IllegalArgumentException(
                    "No puedes eliminar un cierre autorizado. Reábrelo primero.");
        }

        LocalDate fecha = cierre.getFechaCierre();
        String dueno = nombreDe(cierre);

        // Primero los renglones, luego el corte. En este orden.
        detalleRepository.deleteByCierreCaja_IdCierreCaja(id);
        cierreCajaRepository.delete(cierre);

        bitacoraLogger.registrar(
                "cierre_caja",
                id,
                "ELIMINAR",
                "Corte del " + fecha.format(FORMATO_FECHA)
                        + " de " + dueno
                        + " eliminado por " + quienBorra.getNombreUsuario()
        );

        return true;
    }

    // ===================== APOYO =====================

    private BigDecimal valorSeguro(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }

    private String nombreRol(Usuario usuario) {
        return usuario.getRol() != null && usuario.getRol().getNombreRol() != null
                ? usuario.getRol().getNombreRol().trim()
                : "";
    }

    private String nombreDe(CierreCaja cierre) {
        return cierre.getUsuario() != null
                ? cierre.getUsuario().getNombreUsuario()
                : "(sin usuario)";
    }

    private void exigirRolJefe(Usuario usuario, String accion) {
        if (!ROL_JEFE.equalsIgnoreCase(nombreRol(usuario))) {
            throw new IllegalArgumentException(
                    "Solo el jefe puede " + accion + ".");
        }
    }

    private Usuario obtenerUsuarioLogueado() {
        String nombreUsuario = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        return usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new RuntimeException(
                        "Usuario logueado no encontrado en BD: " + nombreUsuario));
    }
}