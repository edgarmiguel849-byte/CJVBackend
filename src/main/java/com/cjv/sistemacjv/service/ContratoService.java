package com.cjv.sistemacjv.service;

import com.cjv.sistemacjv.dto.ContratoConSaldoDTO;
import com.cjv.sistemacjv.dto.ResultadoBusquedaAlumnoDTO;
import com.cjv.sistemacjv.entity.CierreCaja;
import com.cjv.sistemacjv.entity.Contrato;
import com.cjv.sistemacjv.entity.ContratoArticulo;
import com.cjv.sistemacjv.entity.EstadoContrato;
import org.springframework.transaction.annotation.Transactional;
import com.cjv.sistemacjv.entity.OrdenTrabajo;
import com.cjv.sistemacjv.entity.Pago;
import com.cjv.sistemacjv.entity.Paquete;
import com.cjv.sistemacjv.entity.Usuario;
import com.cjv.sistemacjv.repository.CierreCajaRepository;
import com.cjv.sistemacjv.repository.ContratoRepository;
import com.cjv.sistemacjv.repository.EstadoContratoRepository;
import com.cjv.sistemacjv.repository.OrdenTrabajoRepository;
import com.cjv.sistemacjv.repository.PagoRepository;
import com.cjv.sistemacjv.repository.PaqueteRepository;
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
public class ContratoService {

    /** Paquete genérico que se asigna solo, ya que la BD exige id_paquete. */
    private static final String NOMBRE_PAQUETE_DEFECTO = "General";

    /**
     * Estado con el que nace un contrato de cobro sin contrato.
     *
     * La columna id_estado_contrato es NOT NULL, así que un contrato no
     * puede nacer sin estado. Se escribe EXACTAMENTE igual que en la tabla
     * estado_contrato y que en PagoService.actualizarEstadoContrato(): si
     * aquí dijera "Pendiente" a secas, la búsqueda por nombre no lo
     * encontraría y el cobro tronaría al guardar.
     */
    private static final String NOMBRE_ESTADO_PENDIENTE = "Pendiente de pago";

    // Topes de la paginación, para que nadie pida 50,000 registros de un jalón.
    private static final int TAMANIO_MAXIMO_PAGINA = 100;
    private static final int TAMANIO_POR_DEFECTO = 20;

    // Nombres de rol tal como viajan en el token (JwtFilter los pone en
    // MAYÚSCULAS con prefijo ROLE_). En la tabla 'rol' están como
    // "Jefe", "Administrador", "Mostrador".
    private static final String ROL_JEFE = "ROLE_JEFE";
    private static final String ROL_ADMINISTRADOR = "ROLE_ADMINISTRADOR";
    private static final String ROL_MOSTRADOR = "ROLE_MOSTRADOR";

    // Modos de la pantalla de Contratos. Se los mandamos a Angular para que
    // sepa qué mensaje pintar y qué filtros esconder.
    public static final String MODO_SOLO_HOY = "SOLO_HOY";
    public static final String MODO_CORTE_CERRADO = "CORTE_CERRADO";
    public static final String MODO_BUSCAR_PRIMERO = "BUSCAR_PRIMERO";
    public static final String MODO_SIN_ACCESO = "SIN_ACCESO";

    private final ContratoRepository contratoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PagoRepository pagoRepository;
    private final PaqueteRepository paqueteRepository;
    private final CierreCajaRepository cierreCajaRepository;
    private final CierreCajaService cierreCajaService;
    private final EstadoContratoRepository estadoContratoRepository;
    private final OrdenTrabajoRepository ordenTrabajoRepository;
    private final BitacoraLogger bitacoraLogger;
    private final ContratoArticuloService articuloService;

    public ContratoService(ContratoRepository contratoRepository,
                           UsuarioRepository usuarioRepository,
                           PagoRepository pagoRepository,
                           PaqueteRepository paqueteRepository,
                           CierreCajaRepository cierreCajaRepository,
                           CierreCajaService cierreCajaService,
                           EstadoContratoRepository estadoContratoRepository,
                           OrdenTrabajoRepository ordenTrabajoRepository,
                           BitacoraLogger bitacoraLogger,
                           ContratoArticuloService articuloService) {
        this.contratoRepository = contratoRepository;
        this.usuarioRepository = usuarioRepository;
        this.pagoRepository = pagoRepository;
        this.paqueteRepository = paqueteRepository;
        this.cierreCajaRepository = cierreCajaRepository;
        this.cierreCajaService = cierreCajaService;
        this.estadoContratoRepository = estadoContratoRepository;
        this.ordenTrabajoRepository = ordenTrabajoRepository;
        this.bitacoraLogger = bitacoraLogger;
        this.articuloService = articuloService;
    }

    public List<Contrato> listarContratos() {
        return contratoRepository.findAll();
    }

    public Optional<Contrato> buscarPorId(Integer id) {
        return contratoRepository.findById(id);
    }

    /**
     * El contrato con sus ARTÍCULOS cargados, para abrirlo a editar.
     *
     * Es un método aparte de buscarPorId() a propósito: las listas y las
     * matrices no deben cargar artículos (serían cientos de consultas de
     * más). Solo el modal de edición los necesita.
     */
    public Optional<Contrato> buscarParaEditar(Integer id) {
        return contratoRepository.findById(id).map(contrato -> {
            contrato.setArticulos(articuloService.listarParaEditar(contrato));
            return contrato;
        });
    }

    /**
     * ¿Este contrato trae artículos capturados desde la pantalla?
     *
     * Si NO trae, todo se comporta como antes de esta migración: el total
     * llega escrito desde el navegador. Así la pantalla de grupos y el JAR
     * viejo del puerto 8080 siguen funcionando sin cambios.
     */
    private boolean traeArticulos(Contrato contrato) {
        return contrato != null
                && contrato.getArticulos() != null
                && !contrato.getArticulos().isEmpty();
    }

    /**
     * Guarda un contrato nuevo.
     *
     * Si trae artículos, el TOTAL se calcula sumándolos (el número que
     * mande el navegador se ignora) y los renglones se guardan en su tabla
     * después, cuando el contrato ya tiene su id.
     *
     * @Transactional hace que las dos escrituras (contrato + artículos)
     * sean un solo movimiento: si falla la segunda, se deshace la primera
     * y no queda un contrato huérfano sin sus renglones.
     */
    @Transactional
    public Contrato guardarContrato(Contrato contrato) {

        // ===== CANDADO DE CREACIÓN (regla de roles + corte de caja) =====
        // Importa porque ComisionService levanta los anticipos por FECHA
        // DEL CONTRATO. Un contrato capturado hoy con fecha de la semana
        // pasada le mete su anticipo a un corte ya firmado.
        //
        // Devuelve true cuando un ADMINISTRADOR está capturando en un día
        // donde él ya entregó corte: no se le detiene, pero queda anotado.
        boolean avisoDiaTrabado = verificarPermisoCrear(contrato);
        // ================================================================

        normalizarFolio(contrato);
        validarFolioUnico(contrato.getFolio(), null);
        asignarNumeroLista(contrato);
        asignarPaquetePorDefecto(contrato);

        boolean conArticulos = traeArticulos(contrato);
        List<ContratoArticulo> articulos = contrato.getArticulos();

        // El total lo manda el servidor, no el navegador.
        if (conArticulos) {
            contrato.setTotal(articuloService.validarYCalcularTotal(articulos));
        }

        contrato.setUsuario(obtenerUsuarioLogueado());
        Contrato guardado = contratoRepository.save(contrato);

        if (conArticulos) {
            articuloService.reemplazarArticulos(guardado.getIdContrato(), articulos);
            guardado.setArticulos(
                    articuloService.listarPorContrato(guardado.getIdContrato()));
        }

        bitacoraLogger.registrar(
                "contrato",
                guardado.getIdContrato(),
                "CREAR",
                "Contrato #" + guardado.getIdContrato()
                        + (guardado.getFolio() != null ? " (Folio " + guardado.getFolio() + ")" : "")
                        + " registrado - Total: $" + guardado.getTotal()
                        + " - " + describirOrigen(guardado)
                        + (avisoDiaTrabado
                        ? " - AVISO: capturado con fecha " + guardado.getFechaContrato()
                          + ", día donde ya entregó su corte"
                        : "")
        );

        return guardado;
    }

    @Transactional
    public Optional<Contrato> actualizarContrato(Integer id, Contrato datosContrato) {
        return contratoRepository.findById(id).map(contrato -> {

            // ===== CANDADO DE EDICIÓN =====
            // Dos preguntas distintas, las dos tienen que pasar:
            //   1. ¿Este contrato YA viaja en un corte entregado?
            //   2. ¿Puedo mover dinero a la fecha NUEVA? Sin esto, se
            //      podría arrastrar un contrato de hoy a un día ya cortado.
            verificarPermisoModificar(contrato, "editar");
            verificarPuedoMoverDineroEn(datosContrato.getFechaContrato(),
                    "mover a esa fecha");
            // ==============================

            normalizarFolio(datosContrato);
            validarFolioUnico(datosContrato.getFolio(), id);

            Integer idOtAnterior = contrato.getOrdenTrabajo() != null
                    ? contrato.getOrdenTrabajo().getIdOrdenTrabajo()
                    : null;
            Integer idOtNueva = datosContrato.getOrdenTrabajo() != null
                    ? datosContrato.getOrdenTrabajo().getIdOrdenTrabajo()
                    : null;
            boolean cambioDeOrdenTrabajo = !java.util.Objects.equals(idOtAnterior, idOtNueva);

            boolean conArticulos = traeArticulos(datosContrato);
            List<ContratoArticulo> articulos = datosContrato.getArticulos();

            contrato.setCliente(datosContrato.getCliente());
            if (datosContrato.getPaquete() != null
                    && datosContrato.getPaquete().getIdPaquete() != null) {
                contrato.setPaquete(datosContrato.getPaquete());
            }
            contrato.setEstadoContrato(datosContrato.getEstadoContrato());
            contrato.setFolio(datosContrato.getFolio());
            contrato.setOrdenTrabajo(datosContrato.getOrdenTrabajo());
            contrato.setFechaContrato(datosContrato.getFechaContrato());

            // El total: si vienen artículos, manda la suma; si no, se respeta
            // el número que llegó (comportamiento de siempre).
            if (conArticulos) {
                contrato.setTotal(articuloService.validarYCalcularTotal(articulos));
            } else {
                contrato.setTotal(datosContrato.getTotal());
            }

            contrato.setAnticipo(datosContrato.getAnticipo());
            contrato.setModoAnticipo(datosContrato.getModoAnticipo());
            contrato.setObservaciones(datosContrato.getObservaciones());
            contrato.setActivo(datosContrato.getActivo());

            // El arrastre de una recontratación se puede corregir a mano
            // ("Global de recibos anteriores"). Solo se pisa si viene un
            // valor: si llega vacío, se conserva el que ya tenía. Sin este
            // cuidado, editar un contrato desde la pantalla de grupos (que
            // no manda este campo) le borraría el arrastre y le cobraría de
            // más al alumno.
            if (datosContrato.getAbonoHeredado() != null) {
                contrato.setAbonoHeredado(datosContrato.getAbonoHeredado());
            }

            // Datos del alumno capturados en el propio contrato.
            contrato.setNombreAlumno(datosContrato.getNombreAlumno());
            contrato.setTelefono1(datosContrato.getTelefono1());
            contrato.setTelefono2(datosContrato.getTelefono2());
            contrato.setCorreo(datosContrato.getCorreo());

            // Campo 'Ad:' de los adicionales.
            contrato.setAd(datosContrato.getAd());

            // Datos propios del adicional (carrera/escuela/generación) y la
            // fecha de entrega. Sin estas líneas, al editar un adicional se
            // perderían (el backend los ignoraría).
            contrato.setCarrera(datosContrato.getCarrera());
            contrato.setEscuela(datosContrato.getEscuela());
            contrato.setGeneracion(datosContrato.getGeneracion());
            contrato.setFechaEntrega(datosContrato.getFechaEntrega());

            asignarPaquetePorDefecto(contrato);

            if (cambioDeOrdenTrabajo) {
                contrato.setNumeroLista(null);
                asignarNumeroLista(contrato);
            }

            Contrato actualizado = contratoRepository.save(contrato);

            if (conArticulos) {
                articuloService.reemplazarArticulos(actualizado.getIdContrato(), articulos);
                actualizado.setArticulos(
                        articuloService.listarPorContrato(actualizado.getIdContrato()));
            }

            bitacoraLogger.registrar(
                    "contrato",
                    actualizado.getIdContrato(),
                    "EDITAR",
                    "Contrato #" + actualizado.getIdContrato()
                            + (actualizado.getFolio() != null ? " (Folio " + actualizado.getFolio() + ")" : "")
                            + " editado - Total: $" + actualizado.getTotal()
                            + " - " + describirOrigen(actualizado)
            );

            return actualizado;
        });
    }

    @Transactional
    public boolean eliminarContrato(Integer id) {
        Optional<Contrato> encontrado = contratoRepository.findById(id);
        if (encontrado.isEmpty()) {
            return false;
        }

        Contrato contrato = encontrado.get();

        // ===== CANDADO DE ELIMINACIÓN =====
        verificarPermisoModificar(contrato, "eliminar");
        // ==================================

        // Los artículos se van con el contrato. La base también lo hace sola
        // (ON DELETE CASCADE), pero se borra aquí para que quede escrito.
        articuloService.borrarPorContrato(id);

        contratoRepository.deleteById(id);

        bitacoraLogger.registrar(
                "contrato",
                id,
                "ELIMINAR",
                "Contrato #" + id + " eliminado"
        );

        return true;
    }

    /**
     * Devuelve todos los contratos con su saldo calculado:
     * abonado = anticipo + suma de pagos activos; resta = total - abonado.
     */
    public List<ContratoConSaldoDTO> listarContratosConSaldos() {
        List<Contrato> contratos = contratoRepository.findAll();

        return contratos.stream()
                .map(this::calcularSaldoDeContrato)
                .toList();
    }

    // ===================== QUÉ VE CADA ROL =====================

    /**
     * Le dice a la pantalla de Contratos en qué modo debe arrancar,
     * según quién esté logueado. Angular usa esto para pintar el mensaje
     * correcto y esconder los filtros de fecha cuando toca.
     *
     * Los modos:
     *   SOLO_HOY       -> Mostrador con su corte de hoy sin entregar.
     *   CORTE_CERRADO  -> Mostrador que YA entregó su corte de hoy: no ve nada.
     *   BUSCAR_PRIMERO -> Administrador/Jefe: pantalla en blanco hasta buscar.
     *   SIN_ACCESO     -> rol desconocido: no ve nada, por seguridad.
     */
    public ModoPantallaContratos obtenerModoPantalla() {
        String rol = obtenerRolLogueado();
        LocalDate hoy = LocalDate.now();

        if (ROL_JEFE.equals(rol) || ROL_ADMINISTRADOR.equals(rol)) {
            return new ModoPantallaContratos(MODO_BUSCAR_PRIMERO, rol, hoy, false);
        }

        if (ROL_MOSTRADOR.equals(rol)) {
            boolean trabado = yaEntregueMiCorte(hoy);
            return new ModoPantallaContratos(
                    trabado ? MODO_CORTE_CERRADO : MODO_SOLO_HOY,
                    rol, hoy, trabado);
        }

        return new ModoPantallaContratos(MODO_SIN_ACCESO, rol, hoy, true);
    }

    /**
     * Versión paginada de la lista con saldos, para la pantalla de Contratos.
     *
     * AQUÍ VIVE LA REGLA DE QUIÉN VE QUÉ, y vive en el servidor a propósito:
     * esconder botones en Angular es como poner una cortina; esto es la chapa.
     * Aunque alguien manipule la dirección del navegador, el servidor manda.
     *
     *  - MOSTRADOR: si YA entregó su corte de hoy -> no ve NADA.
     *               Si no, se le FUERZAN las fechas a hoy-hoy, sin importar
     *               lo que haya pedido. Puede buscar texto, pero solo dentro
     *               de los contratos de hoy.
     *  - ADMIN/JEFE: si no mandan ningún filtro -> pantalla en blanco.
     *                Con cualquier filtro, buscan libremente.
     */
    public Page<ContratoConSaldoDTO> listarPaginadoConSaldos(String texto,
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
                Sort.by(Sort.Direction.DESC, "fechaContrato")
                        .and(Sort.by(Sort.Direction.DESC, "idContrato"))
        );

        String rol = obtenerRolLogueado();

        // ---------- MOSTRADOR ----------
        if (ROL_MOSTRADOR.equals(rol)) {
            LocalDate hoy = LocalDate.now();

            // SU corte de hoy ya entregado: no ve nada hasta que el jefe reabra.
            if (yaEntregueMiCorte(hoy)) {
                return Page.<ContratoConSaldoDTO>empty(pageable);
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

            // Entran a la pantalla en blanco: los contratos aparecen solo
            // cuando ellos buscan algo o eligen una fecha.
            if (sinNingunFiltro) {
                return Page.<ContratoConSaldoDTO>empty(pageable);
            }
        }
        // ---------- ROL DESCONOCIDO ----------
        else {
            return Page.<ContratoConSaldoDTO>empty(pageable);
        }

        Page<Contrato> pagina1 = contratoRepository
                .buscarPaginado(textoLimpio, desde, hasta, pageable);

        // .map() transforma el contenido pero conserva totalElements y totalPages.
        return pagina1.map(this::calcularSaldoDeContrato);
    }

    /**
     * Versión paginada SOLO para adicionales (contratos sin O.T.), para la
     * pantalla de Contratos Adicionales.
     *
     * Se hizo un método aparte (en vez de meterle un parámetro al de grupos)
     * para NO arriesgar la pantalla de grupos, que ya quedó probada. Las reglas
     * de quién-ve-qué son las MISMAS que en grupos.
     */
    public Page<ContratoConSaldoDTO> listarPaginadoAdicionales(String texto,
                                                               LocalDate desde,
                                                               LocalDate hasta,
                                                               int pagina,
                                                               int tamanio) {

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
                Sort.by(Sort.Direction.DESC, "fechaContrato")
                        .and(Sort.by(Sort.Direction.DESC, "idContrato"))
        );

        String rol = obtenerRolLogueado();

        // ---------- MOSTRADOR ----------
        if (ROL_MOSTRADOR.equals(rol)) {
            LocalDate hoy = LocalDate.now();
            if (yaEntregueMiCorte(hoy)) {
                return Page.<ContratoConSaldoDTO>empty(pageable);
            }
            desde = hoy;
            hasta = hoy;
        }
        // ---------- ADMINISTRADOR Y JEFE ----------
        else if (ROL_JEFE.equals(rol) || ROL_ADMINISTRADOR.equals(rol)) {
            boolean sinNingunFiltro = (textoLimpio == null)
                    && (desde == null)
                    && (hasta == null);
            if (sinNingunFiltro) {
                return Page.<ContratoConSaldoDTO>empty(pageable);
            }
        }
        // ---------- ROL DESCONOCIDO ----------
        else {
            return Page.<ContratoConSaldoDTO>empty(pageable);
        }

        Page<Contrato> paginaAdic = contratoRepository
                .buscarPaginadoAdicionales(textoLimpio, desde, hasta, pageable);

        return paginaAdic.map(this::calcularSaldoDeContrato);
    }


    /**
     * Buscador de alumno: encuentra contratos por nombre del alumno
     * o por folio, y devuelve cada uno con su saldo (abonado / resta).
     *
     * OJO: esta pantalla es aparte y NO se restringe. Mostrador necesita
     * poder buscar a un alumno de cualquier fecha para decirle cuánto debe.
     */
    public List<ResultadoBusquedaAlumnoDTO> buscarAlumno(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }

        List<Contrato> contratos = contratoRepository.buscarAlumno(texto.trim());

        return contratos.stream()
                .map(contrato -> {
                    ContratoConSaldoDTO saldo = calcularSaldoDeContrato(contrato);
                    return new ResultadoBusquedaAlumnoDTO(
                            contrato, saldo.getAbonado(), saldo.getResta());
                })
                .toList();
    }


    /**
     * RECONTRATACIÓN - Paso 1: buscar el contrato ANTERIOR por su folio.
     *
     * Acepta contratos de GRUPO y adicionales: cuando un contrato de grupo
     * vence, el negocio lo recontrata como adicional. Un contrato VENCIDO
     * entra sin problema, porque "Vencido" no es un estado de la base: es
     * un letrero calculado y el contrato sigue Activo.
     */
    public ContratoConSaldoDTO buscarAnteriorParaTraspaso(String folio) {
        Contrato anterior = obtenerAnteriorValidado(folio);
        return calcularSaldoDeContrato(anterior);
    }

    /**
     * Busca el contrato anterior y revisa que se pueda recontratar.
     * Vive aparte porque lo usan los dos pasos, y así las reglas no se
     * escriben dos veces (que es como terminan diciendo cosas distintas).
     */
    private Contrato obtenerAnteriorValidado(String folio) {
        if (folio == null || folio.trim().isEmpty()) {
            throw new IllegalArgumentException("Escribe el folio del contrato anterior.");
        }

        String folioLimpio = folio.trim().toUpperCase();

        Contrato anterior = contratoRepository.findByFolio(folioLimpio)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Contrato inexistente: no se encontró el folio " + folioLimpio + "."));

        // Un contrato cancelado no se recontrata: la cancelación es una
        // decisión humana y manda sobre todo lo demás.
        if (esCancelado(anterior)) {
            throw new IllegalArgumentException(
                    "Contrato cancelado: el folio " + folioLimpio
                            + " no se puede recontratar.");
        }

        // Candado nuevo: como el anterior ya NO se da de baja, hay que evitar
        // que alguien lo recontrate dos veces y duplique el arrastre.
        if (anterior.esTraspasado()) {
            throw new IllegalArgumentException(
                    "El folio " + folioLimpio + " ya fue recontratado en el contrato "
                            + anterior.getTraspasadoA() + ".");
        }

        // Contratos dados de baja por el traspaso viejo entre adicionales.
        if (Boolean.FALSE.equals(anterior.getActivo())) {
            throw new IllegalArgumentException(
                    "El folio " + folioLimpio + " ya fue dado de baja en un traspaso anterior.");
        }

        return anterior;
    }

    /** ¿Está cancelado? Cancelado = el nombre del estado CONTIENE "cancel". */
    private boolean esCancelado(Contrato contrato) {
        if (contrato == null || contrato.getEstadoContrato() == null) {
            return false;
        }
        String estado = contrato.getEstadoContrato().getNombreEstado();
        return estado != null && estado.toLowerCase().contains("cancel");
    }

    /**
     * RECONTRATACIÓN - Paso 2: crea el contrato NUEVO y marca el anterior
     * como recontratado. Todo en una sola transacción: si algo falla, no
     * queda a medias.
     *
     * Cómo queda el dinero (ejemplo: artículos por $2,000, recibos
     * anteriores por $500, hoy entrega $300):
     *
     *   Contrato nuevo:
     *     total           = $2,000  <- la SUMA de los artículos capturados
     *     abonoHeredado   =   $500  <- arrastre; NO cuenta en el corte de caja
     *     anticipo        =   $300  <- dinero de HOY; este SÍ cuenta en caja
     *     abonado         =   $800  (heredado + anticipo)
     *     resta           = $1,200
     *
     *   Contrato anterior:
     *     sigue VIVO (activo = 1) y se queda en su O.T., pintado morado.
     *     traspasadoA = folio del nuevo.
     */
    @Transactional
    public Contrato traspasarAdicional(Contrato nuevo, String folioAnterior) {

        Contrato anterior = obtenerAnteriorValidado(folioAnterior);

        // Lo que el sistema tiene registrado como entregado en el contrato
        // viejo. Es la PROPUESTA de arrastre.
        ContratoConSaldoDTO saldoAnterior = calcularSaldoDeContrato(anterior);
        BigDecimal arrastreCalculado = saldoAnterior.getAbonado() != null
                ? saldoAnterior.getAbonado()
                : BigDecimal.ZERO;

        // Si la persona capturó el global de recibos físicos, ese manda.
        BigDecimal arrastre = nuevo.getAbonoHeredado() != null
                ? nuevo.getAbonoHeredado()
                : arrastreCalculado;

        // Un arrastre negativo no tiene sentido: sería el alumno debiéndole
        // dinero a la joyería desde antes de firmar.
        if (arrastre.compareTo(BigDecimal.ZERO) < 0) {
            arrastre = BigDecimal.ZERO;
        }

        // El nuevo siempre nace como ADICIONAL (sin O.T.), aunque el
        // anterior fuera de grupo: esa es la regla del negocio.
        nuevo.setOrdenTrabajo(null);

        // El total sale de los artículos capturados. Si por alguna razón no
        // llegaron (una pantalla vieja), se conserva el comportamiento de
        // antes: el total histórico del contrato anterior.
        if (!traeArticulos(nuevo)) {
            nuevo.setTotal(anterior.getTotal());
        }

        // El arrastre va en su propio campo, NO en el anticipo.
        nuevo.setAbonoHeredado(arrastre);

        // El anticipo se queda con lo que el alumno pague HOY (puede ser 0).
        if (nuevo.getAnticipo() == null) {
            nuevo.setAnticipo(BigDecimal.ZERO);
        }

        // Un contrato nuevo no nace recontratado.
        nuevo.setTraspasadoA(null);

        // Crea el nuevo (valida folio, calcula el total con los artículos,
        // asigna paquete/usuario, guarda renglones, bitácora).
        Contrato creado = guardarContrato(nuevo);

        // El anterior NO se da de baja: se queda vivo en su O.T., marcado.
        anterior.setTraspasadoA(creado.getFolio());
        contratoRepository.save(anterior);

        bitacoraLogger.registrar(
                "contrato",
                anterior.getIdContrato(),
                "EDITAR",
                "Contrato " + anterior.getFolio() + " RECONTRATADO en "
                        + creado.getFolio()
                        + " - Arrastre: $" + arrastre
                        + " - Anticipo nuevo: $" + creado.getAnticipo()
        );

        return creado;
    }

    // ===================== COBRO SIN CONTRATO =====================

    /**
     * Crea un contrato "invisible" para un cobro sin contrato previo.
     * Caso típico: renta de toga.
     *
     * Sin folio, con nombre obligatorio, pegado a la O.T. que se indique.
     * El total se pone igual al monto que se va a cobrar, para que cuando
     * PagoService le cuelgue el pago encima, la resta quede en cero y el
     * estado sea "Pagado" automáticamente.
     *
     * El anticipo se queda en cero a propósito: el dinero no entra al
     * firmar el contrato (porque no se firma), entra como pago.
     */
    @Transactional
    public Contrato crearContratoRenta(Integer idOrdenTrabajo,
                                       String nombreAlumno,
                                       BigDecimal total,
                                       LocalDate fecha) {

        if (nombreAlumno == null || nombreAlumno.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "El nombre es obligatorio cuando no hay folio de contrato.");
        }
        if (idOrdenTrabajo == null) {
            throw new IllegalArgumentException(
                    "Hace falta la Orden de Trabajo para colgar este cobro.");
        }
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "El monto debe ser mayor a cero.");
        }

        Contrato contrato = new Contrato();
        contrato.setFolio(null);
        contrato.setNombreAlumno(nombreAlumno.trim());

        // La O.T. se TRAE de la base, no se fabrica con el puro ID.
        // Dos razones: así se valida de gratis que la orden exista (si no,
        // el error sería un choque de llave foránea, ilegible), y la
        // bitácora puede escribir su número real en vez de "O.T. null/null".
        OrdenTrabajo ot = ordenTrabajoRepository.findById(idOrdenTrabajo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe la Orden de Trabajo con id " + idOrdenTrabajo + "."));
        contrato.setOrdenTrabajo(ot);

        // La columna id_estado_contrato es NOT NULL: un contrato no puede
        // nacer sin estado. Nace "Pendiente de pago" y en cuanto PagoService
        // le cuelgue el pago, actualizarEstadoContrato() lo pasa solo a
        // "Pagado", porque el total es igual al monto del cobro.
        EstadoContrato pendiente = estadoContratoRepository
                .findByNombreEstado(NOMBRE_ESTADO_PENDIENTE)
                .orElseThrow(() -> new RuntimeException(
                        "No existe el estado '" + NOMBRE_ESTADO_PENDIENTE
                                + "' en la tabla estado_contrato."));
        contrato.setEstadoContrato(pendiente);

        contrato.setFechaContrato(fecha != null ? fecha : LocalDate.now());
        contrato.setTotal(total);
        contrato.setAnticipo(BigDecimal.ZERO);
        // La entidad marca modoAnticipo como NOT NULL, así que no puede ir
        // en null aunque el anticipo sea cero.
        contrato.setModoAnticipo("Efectivo");
        contrato.setAbonoHeredado(BigDecimal.ZERO);
        contrato.setActivo(true);

        // guardarContrato() se encarga del paquete por defecto, número de lista,
        // usuario, bitácora — todo lo que necesita para existir limpio.
        return guardarContrato(contrato);
    }

    private ContratoConSaldoDTO calcularSaldoDeContrato(Contrato contrato) {
        List<Pago> pagos = pagoRepository
                .findByContrato_IdContratoAndActivoTrue(contrato.getIdContrato());

        BigDecimal sumaPagos = pagos.stream()
                .map(Pago::getMontoPago)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // getAbonadoInicial() = anticipo + abono heredado de una recontratación.
        // OJO: aquí SÍ va el heredado, porque estamos calculando lo que el
        // alumno lleva pagado. En el corte de caja NO, porque ese dinero no
        // entró al cajón hoy: viene arrastrado de su contrato anterior.
        BigDecimal abonado = contrato.getAbonadoInicial().add(sumaPagos);
        BigDecimal resta = contrato.getTotal().subtract(abonado);

        // ¿Este contrato en concreto está CONGELADO por un corte?
        // (El nombre del campo sigue siendo 'diaConCorte' para no romper el
        //  frontend, pero ahora la pregunta ya no es por el día: es por este
        //  contrato. Un día puede tener el corte de Tete entregado y el de
        //  Adri abierto.)
        boolean diaConCorte = movimientoCongelado(contrato);

        return new ContratoConSaldoDTO(contrato, abonado, resta, diaConCorte);
    }

    // ===================== CANDADO DE PERMISOS =====================

    /**
     * ¿Ya entregué YO mi corte de ese día?
     *
     * Es la pregunta de la PERSONA. Sirve para movimientos que todavía no
     * existen (crear) o que van a cambiar de fecha (mover). Que Adri haya
     * entregado el suyo no detiene a Tete.
     */
    private boolean yaEntregueMiCorte(LocalDate fecha) {
        if (fecha == null) {
            return false;
        }
        Usuario yo = obtenerUsuarioLogueado();
        return cierreCajaService.yaEntregoSuCorte(fecha, yo.getIdUsuario());
    }

    /**
     * ¿Este contrato está congelado por un corte?
     *
     * Es la pregunta del MOVIMIENTO, y congela para CUALQUIER mostrador,
     * no solo para su dueño: si Tete ya entregó ese contrato y firmó el
     * papel, que Adri se lo cambie a las 6 le rompe el corte sin que ella
     * se entere.
     *
     * AQUÍ VIVE LA FRONTERA:
     *
     *  - Contratos ANTERIORES al 9/09/2026 -> regla VIEJA, por fecha. Es la
     *    única que los protege: nacieron antes de que existiera
     *    cierre_caja_detalle y no tienen renglón ahí. Sin esto quedarían
     *    descongelados de golpe, y entre ellos hay cobros reales.
     *
     *  - De esa fecha en adelante -> regla NUEVA, por renglón en el detalle.
     */
    private boolean movimientoCongelado(Contrato contrato) {
        if (contrato == null || contrato.getFechaContrato() == null) {
            return false;
        }

        if (contrato.getFechaContrato().isBefore(
                CierreCajaService.INICIO_CORTES_POR_PERSONA)) {
            return diaTrabadoReglaVieja(contrato.getFechaContrato());
        }

        return cierreCajaRepository.contratoEstaEnCorteQueTraba(contrato.getIdContrato());
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
     * Regla para CREAR un contrato, según la fecha con la que va a nacer.
     * Un contrato que no existe no puede estar en ningún corte, así que
     * aquí la pregunta correcta es la de la PERSONA.
     *
     *  - JEFE          -> pasa, sin nota.
     *  - ADMINISTRADOR -> pasa SIEMPRE, pero si ya entregó su corte de ese
     *                     día se devuelve true para que quede anotado en
     *                     bitácora. No se le bloquea a propósito: es quien
     *                     captura el grueso de los recibos y detenerlo cada
     *                     vez que tiene un pendiente atrasado terminaría en
     *                     que capture todo con fecha de hoy, que es peor.
     *  - MOSTRADOR     -> bloqueado si ya entregó su corte de ese día.
     *  - Desconocido   -> bloqueado.
     *
     * @return true solo si hay que dejar la nota de aviso.
     */
    private boolean verificarPermisoCrear(Contrato contrato) {
        String rol = obtenerRolLogueado();
        LocalDate fecha = contrato.getFechaContrato();

        if (ROL_JEFE.equals(rol)) {
            return false;
        }

        if (ROL_ADMINISTRADOR.equals(rol)) {
            return yaEntregueMiCorte(fecha);
        }

        if (ROL_MOSTRADOR.equals(rol)) {
            if (yaEntregueMiCorte(fecha)) {
                throw new RuntimeException(
                        "No puedes registrar este contrato: ya entregaste tu corte "
                                + "del " + fecha + ". "
                                + "Pídele al jefe que lo reabra si hay que capturarlo.");
            }
            return false;
        }

        throw new RuntimeException(
                "Tu rol no tiene permiso para registrar contratos.");
    }

    /**
     * ¿Puedo mover dinero a esa FECHA? Pregunta de la persona, para cuando
     * un contrato cambia de fecha al editarse.
     */
    private void verificarPuedoMoverDineroEn(LocalDate fecha, String accion) {
        String rol = obtenerRolLogueado();

        if (ROL_JEFE.equals(rol) || ROL_ADMINISTRADOR.equals(rol)) {
            return;
        }

        if (ROL_MOSTRADOR.equals(rol)) {
            if (yaEntregueMiCorte(fecha)) {
                throw new RuntimeException(
                        "No puedes " + accion + " este contrato: ya entregaste "
                                + "tu corte del " + fecha + ".");
            }
            return;
        }

        throw new RuntimeException(
                "Tu rol no tiene permiso para editar contratos.");
    }

    /**
     * Regla para modificar o eliminar un contrato que YA existe:
     *
     *  - JEFE          -> puede siempre.
     *  - ADMINISTRADOR -> puede siempre.
     *  - MOSTRADOR     -> solo si el contrato NO está congelado dentro de
     *                     un corte entregado.
     *
     * Si el rol no es ninguno de los conocidos, se bloquea por seguridad.
     *
     * @param accion texto para el mensaje de error ("editar" / "eliminar").
     */
    private void verificarPermisoModificar(Contrato contrato, String accion) {
        String rol = obtenerRolLogueado();

        // Jefe y Administrador: sin restricción.
        if (ROL_JEFE.equals(rol) || ROL_ADMINISTRADOR.equals(rol)) {
            return;
        }

        // Mostrador: depende de si ese contrato ya viaja en un corte.
        if (ROL_MOSTRADOR.equals(rol)) {
            if (movimientoCongelado(contrato)) {
                throw new RuntimeException(
                        "No puedes " + accion + " este contrato: ya se entregó "
                                + "dentro de un corte de caja. "
                                + "Pídele al jefe que lo reabra si hay que corregirlo.");
            }
            return;
        }

        // Cualquier otro rol desconocido: se bloquea por seguridad.
        throw new RuntimeException(
                "Tu rol no tiene permiso para " + accion + " contratos.");
    }

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

    // ===================== APOYO PARA ÓRDENES DE TRABAJO =====================

    /**
     * La columna id_paquete es NOT NULL en la base, pero el selector de
     * paquete ya no se muestra en pantalla. Si el contrato llega sin paquete,
     * se le asigna el genérico "General" buscándolo por nombre (no por ID,
     * para que siga funcionando aunque se reinicien los AUTO_INCREMENT).
     */
    private void asignarPaquetePorDefecto(Contrato contrato) {
        if (contrato.getPaquete() != null
                && contrato.getPaquete().getIdPaquete() != null) {
            return;
        }

        Paquete general = paqueteRepository
                .findByNombrePaquete(NOMBRE_PAQUETE_DEFECTO)
                .orElseThrow(() -> new RuntimeException(
                        "No existe el paquete por defecto '" + NOMBRE_PAQUETE_DEFECTO
                                + "'. Créalo en la tabla paquete."));

        contrato.setPaquete(general);
    }

    /**
     * Limpia y valida el folio del contrato.
     * Formato acordado con el negocio: una letra y cuatro números, sin guion.
     * Ejemplos válidos: F6951, A1234.
     *
     * Lo que se teclee en minúscula se guarda en mayúscula, para que no
     * existan "f1234" y "F1234" como si fueran folios distintos.
     */
    private void normalizarFolio(Contrato contrato) {
        String folio = contrato.getFolio();

        if (folio == null) {
            return;
        }

        folio = folio.trim().toUpperCase();

        if (folio.isEmpty()) {
            contrato.setFolio(null);
            return;
        }

        if (!folio.matches("^[A-Z]\\d{4}$")) {
            throw new IllegalArgumentException(
                    "El folio '" + folio + "' no tiene el formato correcto. "
                            + "Debe ser una letra y cuatro números, por ejemplo F6951.");
        }

        contrato.setFolio(folio);
    }

    private void validarFolioUnico(String folio, Integer idContratoActual) {
        if (folio == null) {
            return;
        }
        Optional<Contrato> existente = contratoRepository.findByFolio(folio);
        if (existente.isPresent()
                && !existente.get().getIdContrato().equals(idContratoActual)) {
            throw new RuntimeException("Ya existe un contrato con el folio " + folio);
        }
    }

    private void asignarNumeroLista(Contrato contrato) {
        if (contrato.getOrdenTrabajo() == null
                || contrato.getOrdenTrabajo().getIdOrdenTrabajo() == null) {
            contrato.setNumeroLista(null);
            return;
        }

        Integer maximo = contratoRepository.obtenerMaximoNumeroLista(
                contrato.getOrdenTrabajo().getIdOrdenTrabajo());

        contrato.setNumeroLista(maximo == null ? 1 : maximo + 1);
    }

    private String describirOrigen(Contrato contrato) {
        if (contrato.getOrdenTrabajo() == null) {
            return "Adicional (sin O.T.)";
        }
        return "O.T. " + contrato.getOrdenTrabajo().getNumero()
                + "/" + contrato.getOrdenTrabajo().getAnio()
                + " - lugar " + contrato.getNumeroLista();
    }

    private Usuario obtenerUsuarioLogueado() {
        String nombreUsuario = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        return usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new RuntimeException(
                        "Usuario logueado no encontrado en BD: " + nombreUsuario));
    }

    // ===================== RESPUESTA DEL MODO DE PANTALLA =====================

    /**
     * Paquetito de información que se le manda a Angular para que sepa
     * cómo arrancar la pantalla de Contratos. Va aquí adentro y no en un
     * archivo aparte porque solo lo usa esta pantalla.
     */
    public static class ModoPantallaContratos {

        private final String modo;
        private final String rol;
        private final LocalDate fechaHoy;
        private final boolean corteHoyTrabado;

        public ModoPantallaContratos(String modo, String rol,
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