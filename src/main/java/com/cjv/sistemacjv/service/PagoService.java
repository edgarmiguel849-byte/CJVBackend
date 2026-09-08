package com.cjv.sistemacjv.service;

import com.cjv.sistemacjv.entity.CierreCaja;
import com.cjv.sistemacjv.entity.Contrato;
import com.cjv.sistemacjv.entity.EstadoContrato;
import com.cjv.sistemacjv.entity.Pago;
import com.cjv.sistemacjv.entity.Usuario;
import com.cjv.sistemacjv.repository.CierreCajaRepository;
import com.cjv.sistemacjv.repository.ContratoRepository;
import com.cjv.sistemacjv.repository.EstadoContratoRepository;
import com.cjv.sistemacjv.repository.PagoRepository;
import com.cjv.sistemacjv.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
public class PagoService {

    private final PagoRepository pagoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ContratoRepository contratoRepository;
    private final EstadoContratoRepository estadoContratoRepository;
    private final CierreCajaRepository cierreCajaRepository;
    private final BitacoraLogger bitacoraLogger;
    private final PasswordEncoder passwordEncoder;

    // Formato de fecha para los mensajes al usuario: 15/09/2025
    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Topes de la paginación, para que nadie pida 50,000 registros de un jalón.
    private static final int TAMANIO_MAXIMO_PAGINA = 100;
    private static final int TAMANIO_POR_DEFECTO = 20;

    // RN-11: las devoluciones llevan su propia serie de folios.
    private static final String SERIE_DEVOLUCION = "Z";

    // Roles tal como viajan en el token (los pone el JwtFilter).
    private static final String ROL_JEFE = "ROLE_JEFE";
    private static final String ROL_ADMINISTRADOR = "ROLE_ADMINISTRADOR";
    private static final String ROL_MOSTRADOR = "ROLE_MOSTRADOR";

    public PagoService(PagoRepository pagoRepository,
                       UsuarioRepository usuarioRepository,
                       ContratoRepository contratoRepository,
                       EstadoContratoRepository estadoContratoRepository,
                       CierreCajaRepository cierreCajaRepository,
                       BitacoraLogger bitacoraLogger,
                       PasswordEncoder passwordEncoder) {
        this.pagoRepository = pagoRepository;
        this.usuarioRepository = usuarioRepository;
        this.contratoRepository = contratoRepository;
        this.estadoContratoRepository = estadoContratoRepository;
        this.cierreCajaRepository = cierreCajaRepository;
        this.bitacoraLogger = bitacoraLogger;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Pago> listarPagos() {
        return pagoRepository.findAll();
    }

    /**
     * Lista de pagos paginada para la pantalla.
     * Los tres filtros son opcionales; si llegan vacíos se ignoran.
     * El orden es fecha descendente (lo más reciente arriba) y, para
     * pagos del mismo día, por ID descendente — así el orden nunca
     * cambia entre una página y otra.
     *
     * AQUÍ VIVE LA REGLA DE QUIÉN VE QUÉ, y vive en el servidor a propósito:
     * esconder filtros en Angular es una cortina; esto es la chapa. Aunque
     * alguien manipule la dirección del navegador, el servidor manda.
     *
     *  - MOSTRADOR : si el corte de hoy está trabado -> no ve NADA.
     *                Si no, se le FUERZAN las fechas a hoy-hoy, sin importar
     *                lo que haya pedido. Puede buscar texto, pero solo dentro
     *                de los pagos de hoy.
     *  - ADMIN/JEFE: si no mandan ningún filtro -> pantalla en blanco.
     *                Con cualquier filtro, buscan libremente.
     */
    public Page<Pago> listarPaginado(String texto,
                                     LocalDate desde,
                                     LocalDate hasta,
                                     int pagina,
                                     int tamanio) {

        // Un texto vacío o con puros espacios cuenta como "sin filtro".
        String textoLimpio = (texto == null || texto.trim().isEmpty())
                ? null
                : texto.trim();

        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new IllegalArgumentException(
                    "La fecha final no puede ser anterior a la fecha inicial.");
        }

        int paginaSegura = Math.max(pagina, 0);
        int tamanioSeguro = tamanio <= 0
                ? TAMANIO_POR_DEFECTO
                : Math.min(tamanio, TAMANIO_MAXIMO_PAGINA);

        Pageable pageable = PageRequest.of(
                paginaSegura,
                tamanioSeguro,
                Sort.by(Sort.Direction.DESC, "fechaPago")
                        .and(Sort.by(Sort.Direction.DESC, "idPago"))
        );

        String rol = obtenerRolLogueado();

        // ---------- MOSTRADOR ----------
        if (ROL_MOSTRADOR.equals(rol)) {
            LocalDate hoy = LocalDate.now();

            // Corte del día ya entregado: no ve nada hasta que el jefe reabra.
            if (diaTrabado(hoy)) {
                return Page.<Pago>empty(pageable);
            }

            // Se le pisan las fechas: solo hoy, pase lo que pase.
            desde = hoy;
            hasta = hoy;
        }
        // ---------- ADMINISTRADOR Y JEFE ----------
        else if (ROL_JEFE.equals(rol) || ROL_ADMINISTRADOR.equals(rol)) {

            boolean sinNingunFiltro = (textoLimpio == null)
                    && (desde == null)
                    && (hasta == null);

            // Entran a la pantalla en blanco: los pagos aparecen solo
            // cuando ellos buscan algo o eligen una fecha.
            if (sinNingunFiltro) {
                return Page.<Pago>empty(pageable);
            }
        }
        // ---------- ROL DESCONOCIDO ----------
        else {
            return Page.<Pago>empty(pageable);
        }

        return pagoRepository.buscarPaginado(textoLimpio, desde, hasta, pageable);
    }

    public Optional<Pago> buscarPorId(Integer id) {
        return pagoRepository.findById(id);
    }

    public Pago guardarPago(Pago pago) {
        // CANDADO: Mostrador no registra movimientos en días ya cerrados.
        verificarPermisoSobreFecha(pago.getFechaPago(), "registrar");

        // El orden importa: primero se define QUÉ es el movimiento,
        // porque de eso dependen todas las validaciones siguientes.
        normalizarTipoMovimiento(pago);
        normalizarFolio(pago);
        normalizarMonto(pago);
        validarDevolucion(pago);
        validarCortesia(pago);
        validarFechaPago(pago);
        validarNoDevolverDeMas(pago);

        pago.setUsuario(obtenerUsuarioLogueado());
        Pago guardado = pagoRepository.save(pago);

        bitacoraLogger.registrar(
                "pago",
                guardado.getIdPago(),
                "CREAR",
                descripcionBitacora(guardado, "registrado")
        );

        // Después de guardar, revisar si el contrato ya está pagado.
        actualizarEstadoContrato(guardado.getContrato());

        return guardado;
    }

    public Optional<Pago> actualizarPago(Integer id, Pago datosPago) {
        return pagoRepository.findById(id).map(pago -> {

            // CANDADO: se revisan las DOS fechas, la que el pago TENÍA y la que
            // VA A QUEDAR. Si solo se revisara una, un Mostrador podría agarrar
            // un pago de hoy y arrastrarlo a un día ya cerrado, metiéndose por
            // la puerta de atrás a un corte que ya se entregó.
            verificarPermisoSobreFecha(pago.getFechaPago(), "editar");
            verificarPermisoSobreFecha(datosPago.getFechaPago(), "editar");

            pago.setContrato(datosPago.getContrato());
            // Nota: quien cobró (usuario) NO se cambia al editar.
            pago.setFolio(datosPago.getFolio());
            pago.setFechaPago(datosPago.getFechaPago());
            pago.setMontoPago(datosPago.getMontoPago());
            pago.setModoPago(datosPago.getModoPago());
            pago.setTipoMovimiento(datosPago.getTipoMovimiento());
            // Si el navegador no manda el campo, se toma como NO marcado:
            // la columna es NOT NULL y un null la haría tronar.
            pago.setComisionOficina(Boolean.TRUE.equals(datosPago.getComisionOficina()));
            pago.setComentarios(datosPago.getComentarios());
            pago.setActivo(datosPago.getActivo());

            // Se valida DESPUÉS de asignar los datos nuevos, para revisar
            // el folio, la fecha y el contrato que van a quedar guardados.
            normalizarTipoMovimiento(pago);
            normalizarFolio(pago);
            normalizarMonto(pago);
            validarDevolucion(pago);
            validarCortesia(pago);
            validarFechaPago(pago);
            validarNoDevolverDeMas(pago);

            Pago actualizado = pagoRepository.save(pago);

            bitacoraLogger.registrar(
                    "pago",
                    actualizado.getIdPago(),
                    "EDITAR",
                    descripcionBitacora(actualizado, "editado")
            );

            // Después de editar, revisar si el estado del contrato cambió.
            actualizarEstadoContrato(actualizado.getContrato());

            return actualizado;
        });
    }

    public boolean eliminarPago(Integer id) {
        // Guardar referencia al contrato ANTES de borrar el pago.
        Optional<Pago> pagoOpt = pagoRepository.findById(id);
        Contrato contratoAfectado = pagoOpt.map(Pago::getContrato).orElse(null);

        // CANDADO: Mostrador no borra movimientos de días ya cerrados.
        pagoOpt.ifPresent(p -> verificarPermisoSobreFecha(p.getFechaPago(), "eliminar"));

        if (pagoRepository.existsById(id)) {
            pagoRepository.deleteById(id);

            bitacoraLogger.registrar(
                    "pago",
                    id,
                    "ELIMINAR",
                    "Pago #" + id + " eliminado"
            );

            // Después de eliminar, el contrato podría volver a deber.
            if (contratoAfectado != null) {
                actualizarEstadoContrato(contratoAfectado);
            }

            return true;
        }
        return false;
    }

    // ===================== DEVOLUCIONES (RN-11) =====================

    /**
     * Si nadie dijo qué tipo de movimiento es, es un abono.
     * Esto mantiene funcionando todo lo que ya existía: la pantalla de
     * pagos actual no manda este campo y sigue guardando abonos normales.
     */
    private void normalizarTipoMovimiento(Pago pago) {
        String tipo = pago.getTipoMovimiento();
        if (tipo == null || tipo.trim().isEmpty()) {
            pago.setTipoMovimiento(Pago.TIPO_ABONO);
            return;
        }
        pago.setTipoMovimiento(
                pago.esDevolucion() ? Pago.TIPO_DEVOLUCION : Pago.TIPO_ABONO);
    }

    /**
     * El signo lo decide el servidor, nunca el navegador.
     * El usuario captura 500 y aquí se convierte en -500 si es devolución.
     * Así nadie puede mandar un abono negativo desde afuera.
     */
    private void normalizarMonto(Pago pago) {
        BigDecimal monto = pago.getMontoPago();
        if (monto == null) {
            throw new IllegalArgumentException("Captura el monto del movimiento.");
        }

        BigDecimal absoluto = monto.abs();
        if (absoluto.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("El monto no puede ser cero.");
        }

        pago.setMontoPago(pago.esDevolucion() ? absoluto.negate() : absoluto);
    }

    /**
     * Todo lo que hace especial a una devolución:
     *  - No se devuelve sobre un contrato cancelado.
     *  - No se devuelve una cortesía (nunca entró dinero).
     *  - El comentario es obligatorio: por qué se devolvió.
     *
     * La autorización del jefe se retiró a petición del negocio.
     * Las columnas de la BD siguen existiendo por si se retoma.
     */
    private void validarDevolucion(Pago pago) {
        if (!pago.esDevolucion()) {
            pago.setUsuarioAutoriza(null);
            pago.setContrasenaAutoriza(null);
            return;
        }

        // --- Contrato cancelado: bloqueado ---
        Contrato contratoReal = obtenerContratoReal(pago);
        if (contratoReal != null && esCancelado(contratoReal)) {
            throw new IllegalArgumentException(
                    "El contrato está cancelado. No se devuelve dinero: "
                            + "la papelería y las fotos ya se generaron.");
        }

        // --- Una cortesía no se puede devolver ---
        String modo = pago.getModoPago();
        if (modo != null && modo.trim().equalsIgnoreCase(ComisionService.MODO_CORTESIA)) {
            throw new IllegalArgumentException(
                    "Una cortesía no se puede devolver: nunca entró dinero. "
                            + "Elige por dónde sale el dinero (efectivo, transferencia, etc.).");
        }

        // --- Comentario obligatorio ---
        String comentario = pago.getComentarios();
        if (comentario == null || comentario.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Es una devolución. Escribe en comentarios por qué se "
                            + "devolvió el dinero.");
        }

        // Ya no se pide ni se guarda autorizante.
        pago.setUsuarioAutoriza(null);
        pago.setContrasenaAutoriza(null);
    }

    /**
     * No se puede devolver más dinero del que el alumno entregó.
     * Se suma lo que lleva abonado (sin contar este movimiento, por si
     * se está editando) y se revisa que al aplicarlo no quede en negativo.
     */
    private void validarNoDevolverDeMas(Pago pago) {
        if (!pago.esDevolucion()) {
            return;
        }

        Contrato contrato = obtenerContratoReal(pago);
        if (contrato == null) {
            return;
        }

        // Incluye el abono heredado: ese dinero también lo entregó el alumno,
        // solo que en su contrato anterior.
        BigDecimal anticipo = contrato.getAbonadoInicial();

        List<Pago> pagosActivos = pagoRepository
                .findByContrato_IdContratoAndActivoTrue(contrato.getIdContrato());

        BigDecimal sumaOtros = pagosActivos.stream()
                // Al editar, este mismo pago no debe contarse dos veces.
                .filter(p -> pago.getIdPago() == null
                        || !pago.getIdPago().equals(p.getIdPago()))
                .map(Pago::getMontoPago)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal abonadoActual = anticipo.add(sumaOtros);
        BigDecimal abonadoResultante = abonadoActual.add(pago.getMontoPago());

        if (abonadoResultante.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "No puedes devolver más de lo que el alumno entregó. "
                            + "Lleva abonado $" + abonadoActual
                            + " y estás devolviendo $" + pago.getMontoPago().abs() + ".");
        }
    }

    /** Texto de la bitácora, distinguiendo abono de devolución. */
    private String descripcionBitacora(Pago pago, String accion) {
        String base = pago.esDevolucion() ? "DEVOLUCIÓN #" : "Pago #";
        return base + pago.getIdPago()
                + " " + accion
                + " - Folio: " + (pago.getFolio() != null ? pago.getFolio() : "(sin folio)")
                + " - Monto: $" + pago.getMontoPago()
                + " - Modo: " + (pago.getModoPago() != null ? pago.getModoPago() : "Efectivo");
    }

    private boolean esCancelado(Contrato contrato) {
        return contrato.getEstadoContrato() != null
                && contrato.getEstadoContrato().getNombreEstado() != null
                && contrato.getEstadoContrato().getNombreEstado()
                .toLowerCase().contains("cancel");
    }

    /** Trae el contrato COMPLETO de la BD; el del JSON solo trae el ID. */
    private Contrato obtenerContratoReal(Pago pago) {
        if (pago.getContrato() == null || pago.getContrato().getIdContrato() == null) {
            return null;
        }
        return contratoRepository.findById(pago.getContrato().getIdContrato())
                .orElse(null);
    }

    // ===================== QUIÉN PUEDE QUÉ =====================

    /**
     * Lee el rol del usuario logueado desde el token (lo puso el JwtFilter
     * como una autoridad tipo "ROLE_JEFE"). Devuelve cadena vacía si por
     * alguna razón no hay autoridad, lo que hará que el candado bloquee.
     */
    private String obtenerRolLogueado() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return "";
        }
        return auth.getAuthorities().stream()
                .map(Object::toString)
                .findFirst()
                .orElse("");
    }

    /**
     * Un día está TRABADO cuando ya tiene cierre de caja y ese cierre no
     * está REABIERTO. Es la misma definición que usa ContratoService, para
     * que las dos pantallas no se contradigan.
     */
    private boolean diaTrabado(LocalDate fecha) {
        if (fecha == null) {
            return false;
        }

        List<CierreCaja> cortes =
                cierreCajaRepository.findAllByFechaCierreOrderByIdCierreCajaAsc(fecha);

        if (cortes.isEmpty()) {
            return false; // ese día no tiene corte: está abierto.
        }

        // Basta con que UN corte trabe para que el día esté trabado.
        // Mientras haya un solo corte por día, esto se comporta EXACTAMENTE
        // igual que antes. Cuando existan varios (paso 3), esta regla se
        // afina para que cada quien se trabe únicamente con el suyo.
        return cortes.stream()
                .anyMatch(corte -> CierreCajaService.estadoTrabaElDia(corte.getEstado()));
    }

    /**
     * Candado de verdad para registrar, editar y eliminar movimientos:
     *
     *  - JEFE y ADMINISTRADOR -> siempre pueden, aunque el día esté cerrado.
     *  - MOSTRADOR            -> solo si el día del movimiento NO está trabado.
     *  - Cualquier otro rol   -> bloqueado por seguridad.
     *
     * Se lanza IllegalArgumentException a propósito (y no RuntimeException):
     * el PagoController ya tiene un cazador para ese tipo que lo convierte en
     * un 400 con el mensaje legible, en lugar de un error 500 sin explicación.
     *
     * @param accion texto para el mensaje ("registrar" / "editar" / "eliminar").
     */
    private void verificarPermisoSobreFecha(LocalDate fecha, String accion) {
        String rol = obtenerRolLogueado();

        // Jefe y Administrador: sin restricción.
        if (ROL_JEFE.equals(rol) || ROL_ADMINISTRADOR.equals(rol)) {
            return;
        }

        // Mostrador: depende de si el día del movimiento está trabado.
        if (ROL_MOSTRADOR.equals(rol)) {
            if (diaTrabado(fecha)) {
                throw new IllegalArgumentException(
                        "No puedes " + accion + " este movimiento: el corte del "
                                + fecha.format(FORMATO_FECHA)
                                + " ya fue entregado. "
                                + "Pídele al jefe que lo reabra si hay que corregirlo.");
            }
            return; // día abierto o reabierto: Mostrador sí puede.
        }

        // Cualquier otro rol desconocido: se bloquea por seguridad.
        throw new IllegalArgumentException(
                "Tu rol no tiene permiso para " + accion + " pagos.");
    }

    // ===================== VALIDACIONES DE SIEMPRE =====================

    /**
     * Una cortesía es un paquete regalado. Como no hay dinero de por medio,
     * hay que dejar por escrito a quién se le regaló y por qué:
     * "Representante del grupo", "Hermana de Fernanda", etc.
     */
    private void validarCortesia(Pago pago) {
        String modo = pago.getModoPago();
        if (modo == null || !modo.trim().equalsIgnoreCase(ComisionService.MODO_CORTESIA)) {
            return;
        }

        String comentario = pago.getComentarios();
        if (comentario == null || comentario.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Es una cortesía. Escribe en comentarios a quién se le otorgó "
                            + "y por qué (por ejemplo: representante del grupo).");
        }
    }

    /**
     * Limpia y valida el folio del recibo.
     * Formato acordado con el negocio: una letra y cuatro números, sin guion.
     * Ejemplos válidos: C2672, D1155.
     *
     * El folio es opcional en los abonos: si viene vacío se guarda como null,
     * porque la BD tiene UNIQUE en esa columna y dos recibos con "" chocarían.
     *
     * RN-11: en las devoluciones el folio es OBLIGATORIO y de la serie Z.
     * La serie Z queda reservada: un abono no la puede usar.
     */
    private void normalizarFolio(Pago pago) {
        String folio = pago.getFolio();
        boolean devolucion = pago.esDevolucion();

        if (folio != null) {
            folio = folio.trim().toUpperCase();
        }

        if (folio == null || folio.isEmpty()) {
            if (devolucion) {
                throw new IllegalArgumentException(
                        "Una devolución necesita su folio de la serie Z, "
                                + "por ejemplo Z1234.");
            }
            pago.setFolio(null);
            return;
        }

        if (!folio.matches("^[A-Z]\\d{4}$")) {
            throw new IllegalArgumentException(
                    "El folio '" + folio + "' no tiene el formato correcto. "
                            + "Debe ser una letra y cuatro números, por ejemplo C2672.");
        }

        if (devolucion && !folio.startsWith(SERIE_DEVOLUCION)) {
            throw new IllegalArgumentException(
                    "El folio de una devolución debe empezar con Z, "
                            + "por ejemplo Z1234.");
        }

        if (!devolucion && folio.startsWith(SERIE_DEVOLUCION)) {
            throw new IllegalArgumentException(
                    "La serie Z está reservada para las devoluciones. "
                            + "Usa otra letra para este recibo.");
        }

        pago.setFolio(folio);
    }

    /**
     * Revisa el saldo del contrato y actualiza su estado automáticamente:
     *  - resta <= 0 → "Pagado"
     *  - resta > 0  → "Pendiente de pago"
     *
     * No toca contratos cancelados (esos se manejan aparte).
     *
     * Las devoluciones entran solas en esta cuenta: como están guardadas
     * en negativo, la suma baja y el contrato puede volver a "Pendiente".
     */
    private void actualizarEstadoContrato(Contrato contratoDelPago) {
        if (contratoDelPago == null || contratoDelPago.getIdContrato() == null) {
            return;
        }

        // Traer el contrato REAL de la BD (completo, con su estado actual).
        Contrato contrato = contratoRepository.findById(contratoDelPago.getIdContrato())
                .orElse(null);
        if (contrato == null) {
            return;
        }

        // No tocar contratos cancelados.
        if (esCancelado(contrato)) {
            return;
        }

        // Calcular la resta: total - anticipo - sumaPagos
        BigDecimal total = contrato.getTotal() != null ? contrato.getTotal() : BigDecimal.ZERO;
        // Con heredado: si no, un recontratado nunca llegaría a "Pagado".
        BigDecimal anticipo = contrato.getAbonadoInicial();

        List<Pago> pagosActivos = pagoRepository
                .findByContrato_IdContratoAndActivoTrue(contrato.getIdContrato());
        BigDecimal sumaPagos = pagosActivos.stream()
                .map(Pago::getMontoPago)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal abonado = anticipo.add(sumaPagos);
        BigDecimal resta = total.subtract(abonado);

        // Decidir el estado correcto.
        String estadoCorrecto = resta.compareTo(BigDecimal.ZERO) <= 0 ? "Pagado" : "Pendiente de pago";

        // Solo actualizar si cambió (evita escrituras innecesarias a la BD).
        String estadoActual = contrato.getEstadoContrato() != null
                ? contrato.getEstadoContrato().getNombreEstado()
                : "";

        if (!estadoCorrecto.equals(estadoActual)) {
            EstadoContrato nuevoEstado = estadoContratoRepository.findByNombreEstado(estadoCorrecto)
                    .orElse(null);
            if (nuevoEstado != null) {
                contrato.setEstadoContrato(nuevoEstado);
                contratoRepository.save(contrato);
            }
        }
    }

    /**
     * Guardia de la puerta: un pago NUNCA puede tener fecha anterior a la
     * fecha de su contrato (regla estricta confirmada con el jefe).
     *
     * El contrato que viene del JSON solo trae el ID (lo demás llega null),
     * así que vamos a la BD a buscar el contrato COMPLETO con su fecha real.
     */
    private void validarFechaPago(Pago pago) {
        if (pago == null || pago.getFechaPago() == null || pago.getContrato() == null) {
            return;
        }

        Integer idContrato = pago.getContrato().getIdContrato();
        if (idContrato == null) {
            return;
        }

        // Buscar el contrato REAL en la BD, no confiar en el JSON.
        Contrato contratoReal = contratoRepository.findById(idContrato)
                .orElseThrow(() -> new IllegalArgumentException(
                        "El contrato con ID " + idContrato + " no existe."));

        LocalDate fechaContrato = contratoReal.getFechaContrato();
        if (fechaContrato == null) {
            return;
        }

        LocalDate fechaPago = pago.getFechaPago();
        if (fechaPago.isBefore(fechaContrato)) {
            throw new IllegalArgumentException(
                    "La fecha del pago (" + fechaPago.format(FORMATO_FECHA)
                            + ") no puede ser anterior a la fecha del contrato ("
                            + fechaContrato.format(FORMATO_FECHA) + ")."
            );
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