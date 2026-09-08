package com.cjv.sistemacjv.controller;

import com.cjv.sistemacjv.entity.Contrato;
import com.cjv.sistemacjv.entity.Pago;
import com.cjv.sistemacjv.service.ContratoService;
import com.cjv.sistemacjv.service.PagoService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private final PagoService pagoService;
    private final ContratoService contratoService;

    public PagoController(PagoService pagoService, ContratoService contratoService) {
        this.pagoService = pagoService;
        this.contratoService = contratoService;
    }

    @GetMapping
    public List<Pago> listarPagos() {
        return pagoService.listarPagos();
    }

    /**
     * Lista paginada para la pantalla de Pagos.
     * Ejemplo: /api/pagos/pagina?pagina=0&tamanio=20&texto=F6951&desde=2025-10-01&hasta=2026-06-30
     * Todos los parámetros son opcionales.
     */
    @GetMapping("/pagina")
    public Page<Pago> listarPaginado(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanio
    ) {
        return pagoService.listarPaginado(texto, desde, hasta, pagina, tamanio);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pago> buscarPagoPorId(@PathVariable Integer id) {
        return pagoService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Pago guardarPago(@RequestBody Pago pago) {
        return pagoService.guardarPago(pago);
    }

    /**
     * Cobro sin contrato previo (renta de toga, etc.).
     *
     * La persona no tiene contrato en el sistema, pero sí pertenece a un
     * grupo (una O.T.). El endpoint crea un contrato invisible —sin folio,
     * con el nombre de la persona— y le cuelga el pago encima.
     *
     * Recibe un JSON con todos los campos del pago + dos extras:
     *   idOrdenTrabajo : la O.T. a la que se le cuelga el cobro
     *   nombreAlumno   : nombre de la persona (obligatorio)
     *
     * Todo va en una sola transacción: si el pago falla, el contrato se
     * deshace y no queda basura en la base.
     *
     * El contrato nace con total = monto del cobro y anticipo = 0. Cuando
     * PagoService le cuelga el pago, actualizarEstadoContrato lo marca
     * como "Pagado" automáticamente.
     */
    @PostMapping("/renta")
    @Transactional
    public ResponseEntity<?> registrarRenta(@RequestBody Map<String, Object> datos) {

        // ---------- Extraer los campos ----------
        Integer idOT = extraerEntero(datos, "idOrdenTrabajo");
        String nombre = extraerTexto(datos, "nombreAlumno");
        BigDecimal monto = extraerDecimal(datos, "montoPago");
        String folio = extraerTexto(datos, "folio");
        String fechaTexto = extraerTexto(datos, "fechaPago");
        String modoPago = extraerTexto(datos, "modoPago");
        String tipoMovimiento = extraerTexto(datos, "tipoMovimiento");
        Boolean comisionOficina = extraerBooleano(datos, "comisionOficina");
        String comentarios = extraerTexto(datos, "comentarios");

        LocalDate fecha = (fechaTexto != null && !fechaTexto.isEmpty())
                ? LocalDate.parse(fechaTexto)
                : LocalDate.now();

        // ---------- 1) Crear el contrato invisible ----------
        Contrato contrato = contratoService.crearContratoRenta(
                idOT, nombre, monto != null ? monto.abs() : null, fecha);

        // ---------- 2) Colgarle el pago ----------
        Pago pago = new Pago();
        pago.setContrato(contrato);
        pago.setFolio(folio);
        pago.setFechaPago(fecha);
        pago.setMontoPago(monto != null ? monto.abs() : BigDecimal.ZERO);
        pago.setModoPago(modoPago != null ? modoPago : "Efectivo");
        pago.setTipoMovimiento(tipoMovimiento != null ? tipoMovimiento : Pago.TIPO_ABONO);
        pago.setComisionOficina(Boolean.TRUE.equals(comisionOficina));
        pago.setComentarios(comentarios);
        pago.setActivo(true);

        Pago guardado = pagoService.guardarPago(pago);

        return ResponseEntity.status(HttpStatus.CREATED).body(guardado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Pago> actualizarPago(
            @PathVariable Integer id,
            @RequestBody Pago datosPago
    ) {
        return pagoService.actualizarPago(id, datosPago)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('JEFE')")
    public ResponseEntity<Void> eliminarPago(@PathVariable Integer id) {
        boolean eliminado = pagoService.eliminarPago(id);

        if (eliminado) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.notFound().build();
    }

    /**
     * Red de seguridad: atrapa los errores de validación que lanza el
     * PagoService (por ejemplo, fecha de pago anterior al contrato) y los
     * devuelve como 400 Bad Request con el mensaje para el usuario, en
     * lugar de un 500 feo. Aplica a crear (POST) y editar (PUT).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> manejarValidacion(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    // ===================== UTILERÍA PARA PARSEAR EL MAPA =====================

    private String extraerTexto(Map<String, Object> datos, String campo) {
        Object valor = datos.get(campo);
        return valor != null ? String.valueOf(valor).trim() : null;
    }

    private Integer extraerEntero(Map<String, Object> datos, String campo) {
        Object valor = datos.get(campo);
        if (valor == null) return null;
        if (valor instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(valor).trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private BigDecimal extraerDecimal(Map<String, Object> datos, String campo) {
        Object valor = datos.get(campo);
        if (valor == null) return null;
        if (valor instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(String.valueOf(valor).trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private Boolean extraerBooleano(Map<String, Object> datos, String campo) {
        Object valor = datos.get(campo);
        if (valor == null) return false;
        if (valor instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(String.valueOf(valor).trim());
    }
}