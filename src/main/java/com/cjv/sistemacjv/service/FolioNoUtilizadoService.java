package com.cjv.sistemacjv.service;

import com.cjv.sistemacjv.dto.FolioControlDTO;
import com.cjv.sistemacjv.entity.Contrato;
import com.cjv.sistemacjv.entity.FolioNoUtilizado;
import com.cjv.sistemacjv.entity.Pago;
import com.cjv.sistemacjv.entity.Usuario;
import com.cjv.sistemacjv.repository.ContratoRepository;
import com.cjv.sistemacjv.repository.FolioNoUtilizadoRepository;
import com.cjv.sistemacjv.repository.PagoRepository;
import com.cjv.sistemacjv.repository.UsuarioRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Folios del talonario que NO se usaron.
 *
 * Un folio rojo es un papel que se echó a perder: se guarda el número y
 * la fecha, nada más. No es un contrato ni un pago, y por eso vive en su
 * propia tabla sin tocar el camino del dinero.
 */
@Service
public class FolioNoUtilizadoService {

    /** Mismo formato que exigen ContratoService y PagoService: letra + 4 dígitos. */
    private static final String FORMATO_FOLIO = "^[A-Z]\\d{4}$";

    /**
     * La Z está apartada para devoluciones y no forma parte del recorrido
     * normal del abecedario, que va de la A a la Y.
     */
    private static final String SERIE_RESERVADA = "Z";

    private final FolioNoUtilizadoRepository folioRepository;
    private final ContratoRepository contratoRepository;
    private final PagoRepository pagoRepository;
    private final UsuarioRepository usuarioRepository;
    private final BitacoraLogger bitacoraLogger;

    public FolioNoUtilizadoService(FolioNoUtilizadoRepository folioRepository,
                                   ContratoRepository contratoRepository,
                                   PagoRepository pagoRepository,
                                   UsuarioRepository usuarioRepository,
                                   BitacoraLogger bitacoraLogger) {
        this.folioRepository = folioRepository;
        this.contratoRepository = contratoRepository;
        this.pagoRepository = pagoRepository;
        this.usuarioRepository = usuarioRepository;
        this.bitacoraLogger = bitacoraLogger;
    }

    // ===================== LISTAR =====================

    public List<FolioNoUtilizado> listarPorTipo(String tipo,
                                                LocalDate desde,
                                                LocalDate hasta) {
        String tipoLimpio = validarTipo(tipo);

        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new IllegalArgumentException(
                    "La fecha final no puede ser anterior a la fecha inicial.");
        }

        return folioRepository.buscarPorTipoYRango(tipoLimpio, desde, hasta);
    }

    // ===================== REGISTRAR =====================

    /**
     * Marca un folio como no utilizado.
     *
     * El grueso del método son validaciones, y no sobran: este registro es
     * el que hace que un folio salga ROJO en el reporte de control. Si se
     * pudiera marcar un folio que en realidad sí se usó, el reporte diría
     * que falta un papel que está perfectamente bien, y alguien iría a
     * buscarlo al archivo.
     */
    @Transactional
    public FolioNoUtilizado registrar(String tipo, String folio, LocalDate fecha) {

        String tipoLimpio = validarTipo(tipo);
        String folioLimpio = normalizarFolio(folio);

        if (fecha == null) {
            throw new IllegalArgumentException(
                    "La fecha es obligatoria: es lo único con lo que este folio "
                            + "puede aparecer al filtrar el reporte por periodo.");
        }

        // Ya marcado antes. La base también lo impide con su UNIQUE, pero
        // ese error saldría como un choque ilegible de llave.
        Optional<FolioNoUtilizado> yaMarcado =
                folioRepository.findByTipoAndFolio(tipoLimpio, folioLimpio);
        if (yaMarcado.isPresent()) {
            throw new IllegalArgumentException(
                    "El folio " + folioLimpio + " ya está marcado como no utilizado.");
        }

        // Un folio no puede salir VERDE y ROJO al mismo tiempo. Se revisa
        // contra la tabla que le corresponde a su tipo, nunca contra las
        // otras: el C2672 de recibos y el de contratos son papeles
        // distintos y cada uno lleva su propio abecedario.
        verificarQueNoEsteUsado(tipoLimpio, folioLimpio);

        FolioNoUtilizado registro = new FolioNoUtilizado();
        registro.setTipo(tipoLimpio);
        registro.setFolio(folioLimpio);
        registro.setFecha(fecha);
        registro.setUsuario(obtenerUsuarioLogueado());

        FolioNoUtilizado guardado = folioRepository.save(registro);

        bitacoraLogger.registrar(
                "folio_no_utilizado",
                guardado.getIdFolioNoUtilizado(),
                "CREAR",
                "Folio " + folioLimpio + " (" + tipoLimpio
                        + ") marcado como NO UTILIZADO con fecha " + fecha
        );

        return guardado;
    }

    // ===================== BORRAR =====================

    /**
     * Quita la marca. Hace falta porque marcar un folio equivocado es fácil
     * y, sin esto, ese folio quedaría bloqueado para siempre: no se podría
     * capturar el contrato real con ese número.
     */
    @Transactional
    public boolean eliminar(Integer id) {
        Optional<FolioNoUtilizado> encontrado = folioRepository.findById(id);
        if (encontrado.isEmpty()) {
            return false;
        }

        FolioNoUtilizado registro = encontrado.get();
        folioRepository.deleteById(id);

        bitacoraLogger.registrar(
                "folio_no_utilizado",
                id,
                "ELIMINAR",
                "Se quitó la marca de NO UTILIZADO al folio "
                        + registro.getFolio() + " (" + registro.getTipo() + ")"
        );

        return true;
    }

    // ===================== REPORTE DE CONTROL =====================

    /**
     * Arma el reporte de control de folios de un talonario.
     *
     * Junta dos fuentes en una sola lista: los folios que SÍ se usaron
     * (verdes) y los que se marcaron como no utilizados (rojos). Se ordena
     * por folio, que al ser letra + 4 dígitos de ancho fijo equivale a
     * "abecedario y de menor a mayor" con solo ordenar el texto.
     *
     * SOLO aparecen los folios que existen en la base. Un folio del
     * talonario que nadie tocó no sale: para eso haría falta registrar el
     * rango de cada serie, que se dejó para después.
     *
     * El color NO depende del dinero ni del estado. Un contrato cancelado
     * que se cobró va verde; lo único que se pregunta es si el papel llegó
     * a existir.
     */
    public List<FolioControlDTO> generarControl(String tipo,
                                                LocalDate desde,
                                                LocalDate hasta) {
        String tipoLimpio = validarTipo(tipo);

        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new IllegalArgumentException(
                    "La fecha final no puede ser anterior a la fecha inicial.");
        }

        List<FolioControlDTO> renglones = new ArrayList<>();

        // ---------- VERDES ----------
        if (FolioNoUtilizado.TIPO_RECIBO.equals(tipoLimpio)) {
            for (Pago p : pagoRepository.listarFoliosUsados(desde, hasta)) {
                renglones.add(new FolioControlDTO(
                        p.getFolio(),
                        true,
                        p.getFechaPago(),
                        nombreDelPago(p),
                        p.getMontoPago()));
            }
        } else {
            List<Contrato> contratos =
                    FolioNoUtilizado.TIPO_CONTRATO.equals(tipoLimpio)
                            ? contratoRepository.listarFoliosDeGrupo(desde, hasta)
                            : contratoRepository.listarFoliosDeAdicionales(desde, hasta);

            for (Contrato c : contratos) {
                renglones.add(new FolioControlDTO(
                        c.getFolio(),
                        true,
                        c.getFechaContrato(),
                        c.getNombreAlumno() != null ? c.getNombreAlumno() : "",
                        c.getTotal()));
            }
        }

        // ---------- ROJOS ----------
        for (FolioNoUtilizado f : folioRepository.buscarPorTipoYRango(tipoLimpio, desde, hasta)) {
            renglones.add(new FolioControlDTO(
                    f.getFolio(),
                    false,
                    f.getFecha(),
                    "",
                    BigDecimal.ZERO));
        }

        // Un solo orden para los dos colores: así los rojos caen justo en
        // el hueco que les toca dentro de la secuencia, que es lo que hace
        // legible la hoja.
        renglones.sort(Comparator.comparing(FolioControlDTO::getFolio));

        return renglones;
    }

    /**
     * El nombre que se muestra junto a un recibo.
     *
     * Los contratos NUEVOS lo guardan en nombreAlumno; los VIEJOS lo tenían
     * en cliente.nombreCompleto. Se revisan los dos, en ese orden.
     */
    private String nombreDelPago(Pago p) {
        Contrato c = p.getContrato();
        if (c == null) {
            return "";
        }
        if (c.getNombreAlumno() != null && !c.getNombreAlumno().isBlank()) {
            return c.getNombreAlumno();
        }
        if (c.getCliente() != null && c.getCliente().getNombreCompleto() != null) {
            return c.getCliente().getNombreCompleto();
        }
        return "";
    }

    // ===================== APOYO =====================

    /**
     * ¿Este folio ya existe como papel real?
     *
     * OJO con los adicionales: contratos de grupo y adicionales viven en la
     * MISMA tabla 'contrato' y se distinguen solo por si tienen O.T. o no.
     * Por eso aquí se revisan juntos: si el folio ya está en 'contrato',
     * da igual de cuál de los dos sea, está ocupado.
     */
    private void verificarQueNoEsteUsado(String tipo, String folio) {

        if (FolioNoUtilizado.TIPO_RECIBO.equals(tipo)) {
            // Consulta directa por folio. Antes esto traía la tabla ENTERA
            // de pagos y filtraba en memoria; con tres años de datos eso
            // se nota en cada captura.
            boolean existe = !pagoRepository.findByFolio(folio).isEmpty();
            if (existe) {
                throw new IllegalArgumentException(
                        "El folio " + folio + " ya está registrado en un pago. "
                                + "No se puede marcar como no utilizado.");
            }
            return;
        }

        // CONTRATO y ADICIONAL comparten tabla.
        if (contratoRepository.findByFolio(folio).isPresent()) {
            throw new IllegalArgumentException(
                    "El folio " + folio + " ya está registrado en un contrato. "
                            + "No se puede marcar como no utilizado.");
        }
    }

    private String validarTipo(String tipo) {
        if (tipo == null) {
            throw new IllegalArgumentException("Falta el tipo de folio.");
        }
        String limpio = tipo.trim().toUpperCase();

        if (!FolioNoUtilizado.TIPO_RECIBO.equals(limpio)
                && !FolioNoUtilizado.TIPO_CONTRATO.equals(limpio)
                && !FolioNoUtilizado.TIPO_ADICIONAL.equals(limpio)) {
            throw new IllegalArgumentException(
                    "Tipo de folio desconocido: '" + tipo + "'. "
                            + "Debe ser RECIBO, CONTRATO o ADICIONAL.");
        }
        return limpio;
    }

    /**
     * Misma regla que en contratos y pagos: una letra y cuatro números, en
     * mayúscula. Se escribe aquí y no se importa de ContratoService para no
     * amarrar este servicio al de contratos por una expresión regular.
     */
    private String normalizarFolio(String folio) {
        if (folio == null || folio.trim().isEmpty()) {
            throw new IllegalArgumentException("El folio es obligatorio.");
        }

        String limpio = folio.trim().toUpperCase();

        if (!limpio.matches(FORMATO_FOLIO)) {
            throw new IllegalArgumentException(
                    "El folio '" + limpio + "' no tiene el formato correcto. "
                            + "Debe ser una letra y cuatro números, por ejemplo F6952.");
        }

        if (limpio.startsWith(SERIE_RESERVADA)) {
            throw new IllegalArgumentException(
                    "La serie Z está apartada para devoluciones y no forma parte "
                            + "del talonario.");
        }

        return limpio;
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