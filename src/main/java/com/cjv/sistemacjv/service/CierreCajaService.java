package com.cjv.sistemacjv.service;

import com.cjv.sistemacjv.dto.CorteDiaDTO;
import com.cjv.sistemacjv.entity.CierreCaja;
import com.cjv.sistemacjv.entity.Usuario;
import com.cjv.sistemacjv.repository.CierreCajaRepository;
import com.cjv.sistemacjv.repository.UsuarioRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Maneja el guardado del corte del día: el mostrador cuenta el efectivo y
 * envía; el jefe revisa y autoriza. El jefe también puede devolverlo para
 * corregir (REABIERTO), y entonces el mostrador lo reenvía.
 *
 * Los números se guardan CONGELADOS: son el acta de lo que se contó ese día.
 * Si después alguien edita un pago, el acta no cambia — y esa diferencia
 * entre el acta y el recálculo es justamente la pista de que algo se movió.
 *
 * LOS TRES ESTADOS:
 *   ENVIADO    -> el mostrador entregó el corte. El día queda trabado.
 *   AUTORIZADO -> el jefe lo revisó y lo firmó. El día queda trabado.
 *   REABIERTO  -> el jefe lo devolvió para corregir. El día se DESTRABA
 *                 para que el mostrador arregle y vuelva a enviar.
 */
@Service
public class CierreCajaService {

    public static final String ESTADO_ENVIADO = "ENVIADO";
    public static final String ESTADO_AUTORIZADO = "AUTORIZADO";
    public static final String ESTADO_REABIERTO = "REABIERTO";

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CierreCajaRepository cierreCajaRepository;
    private final UsuarioRepository usuarioRepository;
    private final CorteDiaService corteDiaService;
    private final BitacoraLogger bitacoraLogger;

    public CierreCajaService(CierreCajaRepository cierreCajaRepository,
                             UsuarioRepository usuarioRepository,
                             CorteDiaService corteDiaService,
                             BitacoraLogger bitacoraLogger) {
        this.cierreCajaRepository = cierreCajaRepository;
        this.usuarioRepository = usuarioRepository;
        this.corteDiaService = corteDiaService;
        this.bitacoraLogger = bitacoraLogger;
    }

    public List<CierreCaja> listarCierresCaja() {
        return cierreCajaRepository.findAll();
    }

    public Optional<CierreCaja> buscarPorId(Integer id) {
        return cierreCajaRepository.findById(id);
    }

    /**
     * Devuelve el cierre guardado de un día, o vacío si ese día no se ha
     * cerrado todavía. La pantalla lo usa para decidir qué mostrar.
     */
    public Optional<CierreCaja> buscarPorFecha(LocalDate fecha) {
        return cierreCajaRepository.findByFechaCierre(fecha);
    }

    /** Historial completo, del más reciente al más viejo. */
    public List<CierreCaja> listarHistorial() {
        return cierreCajaRepository.findAllByOrderByFechaCierreDesc();
    }

    /**
     * ¿Ese día está trabado para el mostrador?
     *
     * Trabado = existe un cierre y NO está reabierto. O sea: si el corte
     * está ENVIADO o AUTORIZADO, el mostrador ya no ve ni toca ese día.
     * Si el jefe lo reabrió, se destraba para que se pueda corregir.
     *
     * Este método existe para que la regla viva en UN SOLO lugar y no se
     * repita (mal copiada) en otras partes del sistema.
     */
    public static boolean estadoTrabaElDia(String estadoDelCierre) {
        if (estadoDelCierre == null) {
            // Un cierre sin estado es raro; por seguridad se considera trabado.
            return true;
        }
        return !ESTADO_REABIERTO.equalsIgnoreCase(estadoDelCierre.trim());
    }

    /**
     * El mostrador cuenta el efectivo y envía el cierre del día.
     * El sistema recalcula lo esperado en ese momento (no confía en lo que
     * mande el navegador) y congela los números.
     *
     * Si el día ya tenía un cierre REABIERTO, esto NO crea uno nuevo:
     * actualiza el que ya existe y lo regresa a ENVIADO. Los números se
     * vuelven a calcular desde cero, así que las correcciones sí se ven.
     */
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

        // Un día solo se cierra una vez... salvo que el jefe lo haya
        // reabierto, en cuyo caso este envío es una CORRECCIÓN del anterior.
        Optional<CierreCaja> existente = cierreCajaRepository.findByFechaCierre(fecha);

        if (existente.isPresent()
                && !ESTADO_REABIERTO.equals(existente.get().getEstado())) {
            throw new IllegalArgumentException(
                    "El día " + fecha.format(FORMATO_FECHA) + " ya tiene un cierre. "
                            + "Si necesitas rehacerlo, pídele al jefe que lo reabra.");
        }

        boolean esReenvio = existente.isPresent();

        // Se recalcula aquí, en el servidor. Nunca se confía en el navegador
        // para números de dinero.
        CorteDiaDTO corte = corteDiaService.generar(fecha);

        BigDecimal esperado = valorSeguro(corte.getEfectivoEnCaja());
        BigDecimal ingresos = valorSeguro(corte.getIngresos() != null
                ? corte.getIngresos().getTotalCobrado() : null);
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

        Usuario quienEnvia = obtenerUsuarioLogueado();

        // Si es reenvío se reusa el registro que ya existe (mismo ID, misma
        // fila en la base). Si es la primera vez, se crea uno nuevo.
        CierreCaja cierre = existente.orElseGet(CierreCaja::new);

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

        // Al reenviar, la firma vieja del jefe ya no aplica: se limpia.
        cierre.setUsuarioAutoriza(null);
        cierre.setFechaAutorizacion(null);

        CierreCaja guardado = cierreCajaRepository.save(cierre);

        bitacoraLogger.registrar(
                "cierre_caja",
                guardado.getIdCierreCaja(),
                esReenvio ? "REENVIAR" : "CREAR",
                "Cierre del " + fecha.format(FORMATO_FECHA)
                        + (esReenvio ? " REENVIADO (corregido) por " : " enviado por ")
                        + quienEnvia.getNombreUsuario()
                        + " - Esperado: $" + esperado
                        + " - Contado: $" + efectivoContado
                        + " - Diferencia: $" + diferencia
        );

        return guardado;
    }

    /**
     * El jefe revisa y autoriza. Solo el rol Jefe puede.
     * No se puede autorizar un cierre REABIERTO: primero el mostrador
     * tiene que corregirlo y reenviarlo, porque si no se estaría firmando
     * el acta que justamente se mandó a corregir.
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
                            + "El mostrador debe reenviarlo antes de que puedas autorizarlo.");
        }

        cierre.setEstado(ESTADO_AUTORIZADO);
        cierre.setUsuarioAutoriza(quienAutoriza);
        cierre.setFechaAutorizacion(LocalDateTime.now());

        CierreCaja autorizado = cierreCajaRepository.save(cierre);

        bitacoraLogger.registrar(
                "cierre_caja",
                autorizado.getIdCierreCaja(),
                "AUTORIZAR",
                "Cierre del " + autorizado.getFechaCierre().format(FORMATO_FECHA)
                        + " autorizado por " + quienAutoriza.getNombreUsuario()
        );

        return autorizado;
    }

    /**
     * El jefe devuelve el cierre para que se corrija.
     *
     * Sirve tanto para un cierre ya AUTORIZADO como para uno que apenas
     * está ENVIADO (si el jefe ve el error antes de firmar, no tiene
     * sentido obligarlo a firmar primero).
     *
     * El cierre queda en REABIERTO, que es el único estado que DESTRABA
     * el día para el mostrador: puede volver a ver y editar los contratos
     * de esa fecha, corregir, y reenviar el corte.
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
                "Cierre del " + reabierto.getFechaCierre().format(FORMATO_FECHA)
                        + " reabierto para corrección por " + quienReabre.getNombreUsuario()
        );

        return reabierto;
    }

    /**
     * Borra el cierre de un día para que se pueda rehacer desde cero.
     * Solo el jefe, y solo si NO está autorizado (primero hay que reabrirlo).
     *
     * Con el flujo de REABIERTO ya casi no hace falta borrar, pero se deja
     * como salida de emergencia.
     */
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
        cierreCajaRepository.delete(cierre);

        bitacoraLogger.registrar(
                "cierre_caja",
                id,
                "ELIMINAR",
                "Cierre del " + fecha.format(FORMATO_FECHA)
                        + " eliminado por " + quienBorra.getNombreUsuario()
        );

        return true;
    }

    // ===================== APOYO =====================

    private BigDecimal valorSeguro(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }

    private void exigirRolJefe(Usuario usuario, String accion) {
        String rol = usuario.getRol() != null ? usuario.getRol().getNombreRol() : "";
        if (!"Jefe".equalsIgnoreCase(rol)) {
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