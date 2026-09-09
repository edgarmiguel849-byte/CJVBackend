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
    private final CierreCajaService cierreCajaService;
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
                       CierreCajaService cierreCajaService,
                       BitacoraLogger bitacoraLogger,
                       PasswordEncoder passwordEncoder) {
        this.pagoRepository = pagoRepository;
        this.usuarioRepository = usuarioRepository;
        this.contratoRepository = contratoRepository;
        this.estadoContratoRepository = estadoContratoRepository;
        this.cierreCajaRepository = cierreCajaRepository;
        this.cierreCajaService = cierreCajaService;
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
     *  - MOSTRADOR : si YA entregó su corte de hoy -> no ve NADA.
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

            // SU corte de hoy ya entregado: no ve nada hasta que el jefe reabra.
            if (yaEntregueMiCorte(hoy)) {
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
        // Un pago que todavía no existe no puede estar en ningún corte, así
        // que aquí la pregunta correcta es la de la PERSONA: "¿ya entregué
        // mi corte de ese día?".
        verificarPuedoMoverDineroEn(pago.getFechaPago(), "registrar");

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

            // Dos preguntas distintas, y las dos tienen que pasar:
            //
            //  1. ¿Este pago YA viaja en un corte entregado? Si sí, está
            //     congelado para cualquier mostrador, incluido su dueño.
            //  2. ¿Puedo mover dinero a la fecha NUEVA? Sin esto, Mostrador
            //     agarraría un pago de hoy y lo arrastraría a un día que ya
            //     entregó, metiéndose por la puerta de atrás a un corte
            //     firmado.
            verificarNoCongelado(pago, "editar");
            verificarPuedoMoverDineroEn(datosPago.getFechaPago(), "mover a esa fecha");

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
        if (pagoOpt.isEmpty()) {
            return false;
        }

        Contrato contratoAfectado = pagoOpt.get().getContrato();

        // Un pago ya entregado en un corte no se borra: el papel firmado
        // dejaría de cuadrar con la pantalla.
        verificarNoCongelado(pagoOpt.get(), "eliminar");

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
     * ¿Ya entregué YO mi corte de ese día?
     *
     * Pregunta de la PERSONA. Que Adri haya entregado el suyo no detiene
     * a Tete. Un corte REABIERTO no cuenta: está devuelto para corregir.
     */
    private boolean yaEntregueMiCorte(LocalDate fecha) {
        if (fecha == null) {
            return false;
        }
        Usuario yo = obtenerUsuarioLogueado();
        return cierreCajaService.yaEntregoSuCorte(fecha, yo.getIdUsuario());
    }

    /**
     * ¿Este pago está congelado por un corte?
     *
     * Pregunta del MOVIMIENTO, y congela para CUALQUIER mostrador, no solo
     * para su dueño: si Tete ya entregó ese pago y firmó el papel, que Adri
     * se lo cambie a las 6 le rompe el corte sin que ella se entere.
     *
     * AQUÍ VIVE LA FRONTERA:
     *
     *  - Pagos ANTERIORES al 9/09/2026 -> regla VIEJA, por fecha. Es la
     *    única que los protege: nacieron antes de que existiera
     *    cierre_caja_detalle y no tienen renglón ahí. Sin esto quedarían
     *    descongelados de golpe, y entre ellos hay cobros reales de alumnos.
     *
     *  - De esa fecha en adelante -> regla NUEVA, por renglón en el detalle.
     */
    private boolean movimientoCongelado(Pago pago) {
        if (pago == null || pago.getFechaPago() == null) {
            return false;
        }

        if (pago.getFechaPago().isBefore(
                CierreCajaService.INICIO_CORTES_POR_PERSONA)) {
            return diaTrabadoReglaVieja(pago.getFechaPago());
        }

        return cierreCajaRepository.pagoEstaEnCorteQueTraba(pago.getIdPago());
    }

    /**
     * La regla de antes: el día está trabado si tiene algún corte que trabe.
     * Solo se usa para movimientos anteriores a la frontera.
     */
    private boolean diaTrabadoReglaVieja(LocalDate fecha) {
        List<CierreCaja> cortes =
                cierreCajaRepository.findAllByFechaCierreOrderByIdCierreCajaAsc(fecha);

        return cortes.stream()
                .anyMatch(corte -> CierreCajaService.estadoTrabaElDia(corte.getEstado()));
    }

    /**
     * ¿Puedo mover dinero en esa FECHA? Para crear un movimiento nuevo o
     * cambiarle la fecha a uno que ya existe.
     *
     *  - JEFE y ADMINISTRADOR -> siempre pueden.
     *  - MOSTRADOR            -> solo si ÉL no ha entregado su corte de ese día.
     *  - Cualquier otro rol   -> bloqueado por seguridad.
     *
     * Se lanza IllegalArgumentException a propósito (y no RuntimeException):
     * el PagoController ya tiene un cazador para ese tipo que lo convierte en
     * un 400 con el mensaje legible, en lugar de un error 500 sin explicación.
     */
    private void verificarPuedoMoverDineroEn(LocalDate fecha, String accion) {
        String rol = obtenerRolLogueado();

        if (ROL_JEFE.equals(rol) || ROL_ADMINISTRADOR.equals(rol)) {
            return;
        }

        if (ROL_MOSTRADOR.equals(rol)) {
            if (yaEntregueMiCorte(fecha)) {
                throw new IllegalArgumentException(
                        "No puedes " + accion + " este movimiento: ya entregaste "
                                + "tu corte del " + fecha.format(FORMATO_FECHA) + ". "
                                + "Pídele al jefe que lo reabra si hay que corregirlo.");
            }
            return;
        }

        throw new IllegalArgumentException(
                "Tu rol no tiene permiso para " + accion + " pagos.");
    }

    /**
     * ¿Este pago EN CONCRETO ya viaja en un corte entregado?
     * Para editar y eliminar. Jefe y Administrador siguen pasando.
     */
    private void verificarNoCongelado(Pago pago, String accion) {
        String rol = obtenerRolLogueado();

        if (ROL_JEFE.equals(rol) || ROL_ADMINISTRADOR.equals(rol)) {
            return;
        }

        if (ROL_MOSTRADOR.equals(rol)) {
            if (movimientoCongelado(pago)) {
                throw new IllegalArgumentException(
                        "No puedes " + accion + " este movimiento: ya se entregó "
                                + "dentro de un corte de caja. "
                                + "Pídele al jefe que lo reabra si hay que corregirlo.");
            }
            return;
        }

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