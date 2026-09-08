package com.cjv.sistemacjv.service;

import com.cjv.sistemacjv.dto.RenglonEntregaDTO;
import com.cjv.sistemacjv.dto.ContratoConSaldoDTO;
import com.cjv.sistemacjv.entity.Contrato;
import com.cjv.sistemacjv.entity.Entrega;
import com.cjv.sistemacjv.entity.Pago;
import com.cjv.sistemacjv.entity.Usuario;
import com.cjv.sistemacjv.repository.ContratoRepository;
import com.cjv.sistemacjv.repository.EntregaRepository;
import com.cjv.sistemacjv.repository.PagoRepository;
import com.cjv.sistemacjv.repository.UsuarioRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Registra la entrega de los cuadros al alumno.
 *
 * El saldo que traía el alumno se guarda CONGELADO: si mañana paga lo que
 * debía, el registro debe seguir diciendo que ese día se le entregó debiendo.
 * Es lo que el jefe revisa después.
 */
@Service
public class EntregaService {

    public static final String ESTADO_ENTREGADO = "ENTREGADO";

    private final EntregaRepository entregaRepository;
    private final ContratoRepository contratoRepository;
    private final PagoRepository pagoRepository;
    private final UsuarioRepository usuarioRepository;
    private final BitacoraLogger bitacoraLogger;

    public EntregaService(EntregaRepository entregaRepository,
                          ContratoRepository contratoRepository,
                          PagoRepository pagoRepository,
                          UsuarioRepository usuarioRepository,
                          BitacoraLogger bitacoraLogger) {
        this.entregaRepository = entregaRepository;
        this.contratoRepository = contratoRepository;
        this.pagoRepository = pagoRepository;
        this.usuarioRepository = usuarioRepository;
        this.bitacoraLogger = bitacoraLogger;
    }

    public List<Entrega> listarEntregas() {
        return entregaRepository.findAllByOrderByFechaEntregaDesc();
    }

    public Optional<Entrega> buscarPorId(Integer id) {
        return entregaRepository.findById(id);
    }

    /** Entregas de una O.T., en el orden de la lista del papel. */
    public List<Entrega> listarPorOrdenTrabajo(Integer idOrdenTrabajo) {
        return entregaRepository
                .findByContrato_OrdenTrabajo_IdOrdenTrabajoOrderByContrato_NumeroListaAsc(
                        idOrdenTrabajo);
    }

    /**
     * Las que se entregaron debiendo. Es la lista que revisa el jefe:
     * quién se llevó sus cuadros con saldo y la razón que dejó anotada.
     */
    public List<Entrega> listarConAdeudo() {
        return entregaRepository
                .findBySaldoAlEntregarGreaterThanOrderByFechaEntregaDesc(BigDecimal.ZERO);
    }

    /**
     * La lista completa de una O.T. para la pantalla de Entregas:
     * cada alumno con lo que debe y si ya se le entregó o no.
     *
     * Viene en el orden del papel (por número de lista).
     */
    public List<RenglonEntregaDTO> listarRenglonesDeOrdenTrabajo(Integer idOrdenTrabajo) {
        if (idOrdenTrabajo == null) {
            throw new IllegalArgumentException("Debes indicar la orden de trabajo.");
        }

        List<Contrato> contratos = contratoRepository
                .findByOrdenTrabajo_IdOrdenTrabajoOrderByNumeroListaAsc(idOrdenTrabajo);

        return contratos.stream()
                .map(contrato -> {
                    BigDecimal resta = calcularResta(contrato);

                    BigDecimal total = contrato.getTotal() != null
                            ? contrato.getTotal() : BigDecimal.ZERO;
                    BigDecimal abonado = total.subtract(resta);

                    Entrega entrega = entregaRepository
                            .findByContrato_IdContrato(contrato.getIdContrato())
                            .orElse(null);

                    return new RenglonEntregaDTO(contrato, abonado, resta, entrega);
                })
                .toList();
    }

    /**
     * Registra la entrega de un contrato.
     *
     * El saldo se calcula AQUÍ, en el servidor. Si el alumno debe, el
     * comentario es obligatorio; si está al corriente, se entrega sin más.
     */
    public Entrega entregar(Integer idContrato, String comentarios) {

        if (idContrato == null) {
            throw new IllegalArgumentException("Debes indicar el contrato.");
        }

        Contrato contrato = contratoRepository.findById(idContrato)
                .orElseThrow(() -> new IllegalArgumentException(
                        "El contrato con ID " + idContrato + " no existe."));

        // Un contrato cancelado no tiene cuadros que entregar.
        if (contrato.getEstadoContrato() != null
                && contrato.getEstadoContrato().getNombreEstado() != null
                && contrato.getEstadoContrato().getNombreEstado()
                .toLowerCase().contains("cancel")) {
            throw new IllegalArgumentException(
                    "Ese contrato está cancelado. No se le puede entregar el paquete.");
        }

        // Un alumno solo recibe sus cuadros una vez.
        if (entregaRepository.findByContrato_IdContrato(idContrato).isPresent()) {
            throw new IllegalArgumentException(
                    "A ese alumno ya se le entregó. Revisa la lista.");
        }

        BigDecimal saldo = calcularResta(contrato);

        // Si debe, hay que explicar por qué se le entrega de todos modos.
        String comentarioLimpio = comentarios == null ? "" : comentarios.trim();
        if (saldo.compareTo(BigDecimal.ZERO) > 0 && comentarioLimpio.isEmpty()) {
            throw new IllegalArgumentException(
                    "Ese alumno debe $" + saldo + ". Escribe por qué se le entrega.");
        }

        Usuario quienEntrega = obtenerUsuarioLogueado();

        Entrega entrega = new Entrega();
        entrega.setContrato(contrato);
        entrega.setUsuario(quienEntrega);
        entrega.setFechaEntrega(LocalDate.now());
        entrega.setEstadoEntrega(ESTADO_ENTREGADO);
        entrega.setSaldoAlEntregar(saldo);
        entrega.setComentarios(comentarioLimpio.isEmpty() ? null : comentarioLimpio);
        entrega.setActivo(true);

        Entrega guardada = entregaRepository.save(entrega);

        bitacoraLogger.registrar(
                "entrega",
                guardada.getIdEntrega(),
                "CREAR",
                "Entrega del contrato " + (contrato.getFolio() != null ? contrato.getFolio() : "#" + idContrato)
                        + " a " + nombreAlumno(contrato)
                        + " por " + quienEntrega.getNombreUsuario()
                        + (saldo.compareTo(BigDecimal.ZERO) > 0
                        ? " - CON ADEUDO de $" + saldo
                        : " - sin adeudo")
        );

        return guardada;
    }

    /**
     * Deshace una entrega (se registró por error).
     * Deja el contrato disponible para volver a entregarse.
     */
    public boolean eliminarEntrega(Integer id) {
        Optional<Entrega> encontrada = entregaRepository.findById(id);
        if (encontrada.isEmpty()) {
            return false;
        }

        Entrega entrega = encontrada.get();
        String folio = entrega.getContrato() != null && entrega.getContrato().getFolio() != null
                ? entrega.getContrato().getFolio()
                : "#" + id;

        entregaRepository.delete(entrega);

        bitacoraLogger.registrar(
                "entrega",
                id,
                "ELIMINAR",
                "Entrega del contrato " + folio + " cancelada por "
                        + obtenerUsuarioLogueado().getNombreUsuario()
        );

        return true;
    }

    // ===================== APOYO =====================

    /**     * Lo que le falta pagar al alumno: total - (anticipo + heredado) - pagos.
     * Es el mismo cálculo que usa la pantalla de Contratos.
     */
    private BigDecimal calcularResta(Contrato contrato) {
        BigDecimal total = contrato.getTotal() != null
                ? contrato.getTotal() : BigDecimal.ZERO;
        // Incluye el abono heredado de una recontratación: ese dinero ya lo
        // pagó el alumno, aunque haya sido en su contrato anterior.
        BigDecimal anticipo = contrato.getAbonadoInicial();

        List<Pago> pagos = pagoRepository
                .findByContrato_IdContratoAndActivoTrue(contrato.getIdContrato());

        BigDecimal sumaPagos = pagos.stream()
                .map(Pago::getMontoPago)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return total.subtract(anticipo).subtract(sumaPagos);
    }

    private String nombreAlumno(Contrato contrato) {
        if (contrato.getCliente() == null) {
            return "(sin cliente)";
        }
        return contrato.getCliente().getNombreCompleto();
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