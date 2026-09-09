package com.cjv.sistemacjv.service;

import com.cjv.sistemacjv.dto.PaginaEgresosDTO;
import com.cjv.sistemacjv.entity.CierreCaja;
import com.cjv.sistemacjv.entity.Egreso;
import com.cjv.sistemacjv.entity.Usuario;
import com.cjv.sistemacjv.repository.CierreCajaRepository;
import com.cjv.sistemacjv.repository.EgresoRepository;
import com.cjv.sistemacjv.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class EgresoService {

    // Topes de la paginación, para que nadie pida 50,000 registros de un jalón.
    private static final int TAMANIO_MAXIMO_PAGINA = 100;
    private static final int TAMANIO_POR_DEFECTO = 20;

    // Los roles llegan en el token como autoridades "ROLE_XXX" (las puso
    // el JwtFilter). Se repiten aquí, y no se importan de ContratoService,
    // para no amarrar Egresos a la pantalla de Contratos.
    private static final String ROL_JEFE = "ROLE_JEFE";
    private static final String ROL_ADMINISTRADOR = "ROLE_ADMINISTRADOR";
    private static final String ROL_MOSTRADOR = "ROLE_MOSTRADOR";

    // Mismos nombres de modo que usa la pantalla de Contratos, para que
    // Angular no tenga que aprenderse dos vocabularios.
    public static final String MODO_SOLO_HOY = "SOLO_HOY";
    public static final String MODO_CORTE_CERRADO = "CORTE_CERRADO";
    public static final String MODO_BUSCAR_PRIMERO = "BUSCAR_PRIMERO";
    public static final String MODO_SIN_ACCESO = "SIN_ACCESO";

    private final EgresoRepository egresoRepository;
    private final UsuarioRepository usuarioRepository;
    private final BitacoraLogger bitacoraLogger;
    private final CierreCajaRepository cierreCajaRepository;
    private final CierreCajaService cierreCajaService;

    public EgresoService(EgresoRepository egresoRepository,
                         UsuarioRepository usuarioRepository,
                         BitacoraLogger bitacoraLogger,
                         CierreCajaRepository cierreCajaRepository,
                         CierreCajaService cierreCajaService) {
        this.egresoRepository = egresoRepository;
        this.usuarioRepository = usuarioRepository;
        this.bitacoraLogger = bitacoraLogger;
        this.cierreCajaRepository = cierreCajaRepository;
        this.cierreCajaService = cierreCajaService;
    }

    // ===================== MODO DE PANTALLA =====================

    /**
     * Le dice a Angular cómo arrancar la pantalla de Egresos.
     * Es SOLO para pintar: los candados de verdad están más abajo y en
     * el filtrado. Si esto se manipulara, la lista seguiría saliendo
     * vacía y el guardado seguiría rebotando.
     */
    public ModoPantallaEgresos obtenerModoPantalla() {
        String rol = obtenerRolLogueado();
        LocalDate hoy = LocalDate.now();

        if (ROL_JEFE.equals(rol) || ROL_ADMINISTRADOR.equals(rol)) {
            return new ModoPantallaEgresos(MODO_BUSCAR_PRIMERO, rol, hoy, false);
        }

        if (ROL_MOSTRADOR.equals(rol)) {
            boolean trabado = yaEntregueMiCorte(hoy);
            return new ModoPantallaEgresos(
                    trabado ? MODO_CORTE_CERRADO : MODO_SOLO_HOY,
                    rol, hoy, trabado);
        }

        return new ModoPantallaEgresos(MODO_SIN_ACCESO, rol, hoy, true);
    }

    public List<Egreso> listarEgresos() {
        return egresoRepository.findAll();
    }

    public Optional<Egreso> buscarPorId(Integer id) {
        return egresoRepository.findById(id);
    }

    /**
     * Egresos activos de un rango de fechas (para el cierre de caja).
     */
    public List<Egreso> listarPorRango(LocalDate desde, LocalDate hasta) {
        return egresoRepository.findByActivoTrueAndFechaEgresoBetween(desde, hasta);
    }

    /**
     * Lista de egresos paginada para la pantalla.
     * Los tres filtros son opcionales; si llegan vacíos se ignoran.
     * El orden es fecha descendente y, para egresos del mismo día,
     * por ID descendente, así el orden no cambia entre páginas.
     *
     * Devuelve la página MÁS el total de todos los que cumplen el filtro,
     * no solo el de los registros visibles.
     */
    public PaginaEgresosDTO listarPaginado(String texto,
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
                Sort.by(Sort.Direction.DESC, "fechaEgreso")
                        .and(Sort.by(Sort.Direction.DESC, "idEgreso"))
        );

        // ===================== QUIÉN VE QUÉ =====================
        //
        // Esta regla vive en el SERVIDOR a propósito. Esconder renglones en
        // Angular es una cortina; esto es la chapa. Aunque alguien arme la
        // petición a mano con las fechas que se le antojen, aquí se le pisan.
        //
        // Es el mismo criterio de ContratoService.listarPaginadoConSaldos().

        String rol = obtenerRolLogueado();

        // ---------- MOSTRADOR ----------
        if (ROL_MOSTRADOR.equals(rol)) {
            LocalDate hoy = LocalDate.now();

            // SU corte de hoy ya entregado: no ve nada hasta que el jefe reabra.
            if (yaEntregueMiCorte(hoy)) {
                return new PaginaEgresosDTO(Page.<Egreso>empty(pageable), BigDecimal.ZERO);
            }

            // Se le pisan las fechas: solo hoy, pase lo que pase. Puede
            // seguir buscando texto, pero nada más dentro de los de hoy.
            desde = hoy;
            hasta = hoy;
        }
        // ---------- ADMINISTRADOR Y JEFE ----------
        else if (ROL_JEFE.equals(rol) || ROL_ADMINISTRADOR.equals(rol)) {

            boolean sinNingunFiltro = (textoLimpio == null)
                    && (desde == null)
                    && (hasta == null);

            // Entran a la pantalla en blanco: los egresos aparecen solo
            // cuando ellos buscan algo o eligen una fecha.
            if (sinNingunFiltro) {
                return new PaginaEgresosDTO(Page.<Egreso>empty(pageable), BigDecimal.ZERO);
            }
        }
        // ---------- ROL DESCONOCIDO ----------
        else {
            return new PaginaEgresosDTO(Page.<Egreso>empty(pageable), BigDecimal.ZERO);
        }

        Page<Egreso> paginaEgresos = egresoRepository
                .buscarPaginado(textoLimpio, desde, hasta, pageable);

        // OJO: la suma se calcula con las MISMAS fechas ya pisadas de arriba.
        // Si se calculara con las que mandó el navegador, a Mostrador le
        // saldrían 5 renglones de hoy con el TOTAL de todos los tiempos.
        // La suma llega null cuando no hay ningún egreso que cumpla el filtro.
        BigDecimal total = egresoRepository
                .sumarConFiltros(textoLimpio, desde, hasta);
        if (total == null) {
            total = BigDecimal.ZERO;
        }

        return new PaginaEgresosDTO(paginaEgresos, total);
    }

    public Egreso guardarEgreso(Egreso egreso) {
        // Un egreso que todavía no existe no puede estar dentro de ningún
        // corte, así que aquí la pregunta correcta es la de la PERSONA:
        // "¿ya entregué mi corte de ese día?". Sin esto, Mostrador podría
        // capturar hoy un egreso fechado la semana pasada y meterle una
        // salida de efectivo a un corte ya firmado.
        verificarPuedoMoverDineroEn(egreso.getFechaEgreso(), "registrar");

        // El autor se toma del token, igual que en pagos y contratos.
        egreso.setUsuario(obtenerUsuarioLogueado());

        Egreso guardado = egresoRepository.save(egreso);

        bitacoraLogger.registrar(
                "egreso",
                guardado.getIdEgreso(),
                "CREAR",
                "Egreso #" + guardado.getIdEgreso()
                        + " registrado - " + guardado.getConcepto()
                        + " - Monto: $" + guardado.getMonto()
        );

        return guardado;
    }

    public Optional<Egreso> actualizarEgreso(Integer id, Egreso datosEgreso) {
        return egresoRepository.findById(id).map(egreso -> {
            // Dos preguntas distintas, y las dos tienen que pasar:
            //
            //  1. ¿Este egreso YA viaja en un corte entregado? Si sí, está
            //     congelado para cualquier mostrador — incluido su dueño.
            //  2. ¿Puedo mover dinero a la fecha NUEVA? Sin esto, Mostrador
            //     agarraría un egreso de hoy y lo empujaría a un día que ya
            //     entregó.
            verificarNoCongelado(egreso, "editar");
            verificarPuedoMoverDineroEn(datosEgreso.getFechaEgreso(), "mover a esa fecha");

            // Nota: el usuario que registró NO se cambia al editar.
            egreso.setConcepto(datosEgreso.getConcepto());
            egreso.setMonto(datosEgreso.getMonto());
            egreso.setFechaEgreso(datosEgreso.getFechaEgreso());
            egreso.setComentarios(datosEgreso.getComentarios());
            egreso.setActivo(datosEgreso.getActivo());

            Egreso actualizado = egresoRepository.save(egreso);

            bitacoraLogger.registrar(
                    "egreso",
                    actualizado.getIdEgreso(),
                    "EDITAR",
                    "Egreso #" + actualizado.getIdEgreso()
                            + " editado - " + actualizado.getConcepto()
                            + " - Monto: $" + actualizado.getMonto()
            );

            return actualizado;
        });
    }

    public boolean eliminarEgreso(Integer id) {
        Optional<Egreso> encontrado = egresoRepository.findById(id);
        if (encontrado.isEmpty()) {
            return false;
        }

        // CANDADO QUE ANTES NO EXISTÍA: eliminarEgreso() no revisaba nada.
        // Un egreso ya entregado en un corte no se puede borrar: el papel
        // firmado dejaría de cuadrar con la pantalla.
        verificarNoCongelado(encontrado.get(), "eliminar");

        egresoRepository.deleteById(id);

        bitacoraLogger.registrar(
                "egreso",
                id,
                "ELIMINAR",
                "Egreso #" + id + " eliminado"
        );

        return true;
    }

    // ===================== CANDADOS =====================

    /**
     * ¿Puedo mover dinero en esa FECHA?
     *
     * Es la pregunta de la PERSONA, para movimientos que todavía no
     * existen (crear) o que van a cambiar de fecha (mover).
     *
     *  - JEFE y ADMINISTRADOR -> siempre pueden.
     *  - MOSTRADOR            -> solo si ÉL no ha entregado su corte de
     *                            ese día. Que Adri ya haya entregado el
     *                            suyo no lo detiene.
     *  - Rol desconocido      -> bloqueado.
     */
    private void verificarPuedoMoverDineroEn(LocalDate fecha, String accion) {
        String rol = obtenerRolLogueado();

        if (ROL_JEFE.equals(rol) || ROL_ADMINISTRADOR.equals(rol)) {
            return;
        }

        if (ROL_MOSTRADOR.equals(rol)) {
            if (yaEntregueMiCorte(fecha)) {
                throw new RuntimeException(
                        "No puedes " + accion + " este egreso: ya entregaste tu "
                                + "corte del " + fecha + ". "
                                + "Pídele al jefe que lo reabra si hay que corregirlo.");
            }
            return;
        }

        throw new RuntimeException(
                "Tu rol no tiene permiso para " + accion + " egresos.");
    }

    /**
     * ¿Este egreso EN CONCRETO ya viaja en un corte entregado?
     *
     * Es la pregunta del MOVIMIENTO, para editar y eliminar. Y a diferencia
     * de la de arriba, congela para CUALQUIER mostrador, no solo para su
     * dueño: si Tete ya entregó ese egreso y firmó el papel, que Adri se lo
     * cambie a las 6 de la tarde le rompe el corte y ella ni se entera.
     *
     * Jefe y Administrador siguen pasando, como en todo el sistema.
     */
    private void verificarNoCongelado(Egreso egreso, String accion) {
        String rol = obtenerRolLogueado();

        if (ROL_JEFE.equals(rol) || ROL_ADMINISTRADOR.equals(rol)) {
            return;
        }

        if (ROL_MOSTRADOR.equals(rol)) {
            if (movimientoCongelado(egreso)) {
                throw new RuntimeException(
                        "No puedes " + accion + " este egreso: ya se entregó "
                                + "dentro de un corte de caja. "
                                + "Pídele al jefe que lo reabra si hay que corregirlo.");
            }
            return;
        }

        throw new RuntimeException(
                "Tu rol no tiene permiso para " + accion + " egresos.");
    }

    /**
     * ¿Ya entregué YO mi corte de ese día?
     *
     * Un corte REABIERTO no cuenta: está devuelto justo para corregirlo.
     */
    private boolean yaEntregueMiCorte(LocalDate fecha) {
        if (fecha == null) {
            return false;
        }
        Usuario yo = obtenerUsuarioLogueado();
        return cierreCajaService.yaEntregoSuCorte(fecha, yo.getIdUsuario());
    }

    /**
     * ¿Este egreso está congelado por un corte?
     *
     * AQUÍ VIVE LA FRONTERA:
     *
     *  - Egresos ANTERIORES al 9/09/2026 -> regla VIEJA, por fecha. Es la
     *    única que los protege: nacieron antes de que existiera
     *    cierre_caja_detalle y no tienen renglón ahí. Sin esto quedarían
     *    descongelados de golpe, y entre ellos hay movimientos reales.
     *
     *  - De esa fecha en adelante -> regla NUEVA, por renglón en el
     *    detalle. Es la que permite que el corte de Tete no congele lo
     *    de Adri.
     */
    private boolean movimientoCongelado(Egreso egreso) {
        if (egreso == null || egreso.getFechaEgreso() == null) {
            return false;
        }

        if (egreso.getFechaEgreso().isBefore(
                CierreCajaService.INICIO_CORTES_POR_PERSONA)) {
            return diaTrabadoReglaVieja(egreso.getFechaEgreso());
        }

        return cierreCajaRepository.egresoEstaEnCorteQueTraba(egreso.getIdEgreso());
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
     * Lee el rol del token (lo puso el JwtFilter como autoridad "ROLE_JEFE").
     * Devuelve cadena vacía si no hay autoridad, lo que hace que el candado
     * bloquee en vez de dejar pasar.
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

    private Usuario obtenerUsuarioLogueado() {
        String nombreUsuario = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        return usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new RuntimeException(
                        "Usuario logueado no encontrado en BD: " + nombreUsuario));
    }

    /**
     * Paquetito que se le manda a Angular para que sepa cómo arrancar la
     * pantalla de Egresos. Mismos campos que ModoPantallaContratos.
     */
    public static class ModoPantallaEgresos {

        private final String modo;
        private final String rol;
        private final LocalDate fechaHoy;
        private final boolean corteHoyTrabado;

        public ModoPantallaEgresos(String modo, String rol,
                                   LocalDate fechaHoy, boolean corteHoyTrabado) {
            this.modo = modo;
            this.rol = rol;
            this.fechaHoy = fechaHoy;
            this.corteHoyTrabado = corteHoyTrabado;
        }

        public String getModo() {
            return modo;
        }

        public String getRol() {
            return rol;
        }

        public LocalDate getFechaHoy() {
            return fechaHoy;
        }

        public boolean isCorteHoyTrabado() {
            return corteHoyTrabado;
        }
    }
}