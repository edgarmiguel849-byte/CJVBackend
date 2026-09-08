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

    public EgresoService(EgresoRepository egresoRepository,
                         UsuarioRepository usuarioRepository,
                         BitacoraLogger bitacoraLogger,
                         CierreCajaRepository cierreCajaRepository) {
        this.egresoRepository = egresoRepository;
        this.usuarioRepository = usuarioRepository;
        this.bitacoraLogger = bitacoraLogger;
        this.cierreCajaRepository = cierreCajaRepository;
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
            boolean trabado = diaTrabado(hoy);
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

            // Corte del día ya entregado: no ve nada hasta que el jefe reabra.
            if (diaTrabado(hoy)) {
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
        // Se valida la fecha CON LA QUE VA A NACER. Sin esto, Mostrador
        // podría capturar hoy un egreso fechado la semana pasada y meterle
        // una salida de efectivo a un corte ya firmado.
        verificarPermisoSobreFecha(egreso.getFechaEgreso(), "registrar");

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
            // Se revisan las DOS fechas, no solo una:
            //  - la que tiene guardada, para no tocar un corte ya cerrado;
            //  - la que llega, para no mover el egreso HACIA un corte cerrado.
            // Con revisar solo la vieja, Mostrador podría agarrar un egreso
            // de hoy y empujarlo a un día ya entregado.
            verificarPermisoSobreFecha(egreso.getFechaEgreso(), "editar");
            verificarPermisoSobreFecha(datosEgreso.getFechaEgreso(), "mover a esa fecha");

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
        if (egresoRepository.existsById(id)) {
            egresoRepository.deleteById(id);

            bitacoraLogger.registrar(
                    "egreso",
                    id,
                    "ELIMINAR",
                    "Egreso #" + id + " eliminado"
            );

            return true;
        }
        return false;
    }

    // ===================== CANDADOS =====================

    /**
     * Regla para crear, editar o mover un egreso a una fecha:
     *
     *  - JEFE y ADMINISTRADOR -> siempre pueden.
     *  - MOSTRADOR            -> solo si ese día NO está trabado.
     *  - Rol desconocido      -> bloqueado.
     *
     * Es la misma regla que ContratoService.verificarPermisoModificar(),
     * porque un egreso y un contrato le pegan al mismo corte.
     */
    private void verificarPermisoSobreFecha(LocalDate fecha, String accion) {
        String rol = obtenerRolLogueado();

        if (ROL_JEFE.equals(rol) || ROL_ADMINISTRADOR.equals(rol)) {
            return;
        }

        if (ROL_MOSTRADOR.equals(rol)) {
            if (diaTrabado(fecha)) {
                throw new RuntimeException(
                        "No puedes " + accion + " este egreso: el corte del "
                                + fecha + " ya fue entregado. "
                                + "Pídele al jefe que lo reabra si hay que corregirlo.");
            }
            return; // día abierto o reabierto: Mostrador sí puede.
        }

        throw new RuntimeException(
                "Tu rol no tiene permiso para " + accion + " egresos.");
    }

    /**
     * Un día está TRABADO cuando tiene al menos un corte y ese corte no
     * está REABIERTO.
     *
     * Se pregunta por TODOS los cortes de la fecha, no por uno solo: un día
     * puede tener varios (cada persona entrega el suyo, y el Administrativo
     * puede hacer más de uno). Con un solo corte por día — como hoy — el
     * resultado es idéntico al de antes.
     *
     * Qué estados traban NO se decide aquí: se le pregunta a
     * CierreCajaService.estadoTrabaElDia(), para que esa regla exista
     * escrita en un solo lugar de todo el sistema.
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
        // En el paso 3 esta regla se afina para que cada quien se trabe
        // únicamente con el suyo.
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