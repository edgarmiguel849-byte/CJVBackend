package com.cjv.sistemacjv.controller;

import com.cjv.sistemacjv.dto.FolioControlDTO;
import com.cjv.sistemacjv.entity.FolioNoUtilizado;
import com.cjv.sistemacjv.service.FolioNoUtilizadoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/folios-no-utilizados")
@CrossOrigin(origins = "*")
public class FolioNoUtilizadoController {

    private final FolioNoUtilizadoService folioService;

    public FolioNoUtilizadoController(FolioNoUtilizadoService folioService) {
        this.folioService = folioService;
    }

    /**
     * Los folios muertos de un tipo, opcionalmente acotados a un periodo.
     *
     * Ejemplo: /api/folios-no-utilizados?tipo=RECIBO&desde=2026-09-01
     */
    @GetMapping
    public List<FolioNoUtilizado> listar(
            @RequestParam String tipo,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        return folioService.listarPorTipo(tipo, desde, hasta);
    }

    /**
     * El reporte de control: verdes y rojos de un talonario, en una sola
     * lista ya ordenada. Solo lo consulta el Jefe.
     *
     * Ejemplo: /api/folios-no-utilizados/control?tipo=RECIBO&desde=2026-09-01
     */
    @GetMapping("/control")
    @PreAuthorize("hasRole('JEFE')")
    public List<FolioControlDTO> control(
            @RequestParam String tipo,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        return folioService.generarControl(tipo, desde, hasta);
    }

    /**
     * Marca un folio como no utilizado.
     *
     * El cuerpo se lee como Map y no como entidad a propósito: si se
     * recibiera un FolioNoUtilizado, alguien podría mandar el id_usuario
     * en el JSON y quedaría registrado a nombre de otra persona. El
     * usuario se toma del token, en el servicio.
     */
    @PostMapping
    public ResponseEntity<?> registrar(@RequestBody Map<String, Object> datos) {

        String tipo = extraerTexto(datos.get("tipo"));
        String folio = extraerTexto(datos.get("folio"));
        LocalDate fecha = extraerFecha(datos.get("fecha"));

        FolioNoUtilizado guardado = folioService.registrar(tipo, folio, fecha);
        return ResponseEntity.status(HttpStatus.CREATED).body(guardado);
    }

    /** Quita la marca, para cuando se capturó un folio equivocado. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        return folioService.eliminar(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    // ===================== APOYO =====================

    /**
     * Saca texto de un campo del JSON.
     *
     * OJO con String.valueOf(): si el valor llega en null, devuelve la
     * cadena "null" de cuatro letras y esa palabra terminaría guardada en
     * la base como si fuera un dato. Por eso el null se revisa ANTES.
     */
    private String extraerTexto(Object valor) {
        if (valor == null) {
            return null;
        }
        String texto = String.valueOf(valor).trim();
        return texto.isEmpty() ? null : texto;
    }

    private LocalDate extraerFecha(Object valor) {
        String texto = extraerTexto(valor);
        if (texto == null) {
            return null;
        }
        try {
            // Se recorta a 10 caracteres por si llega un ISO con hora:
            // LocalDate.parse revienta con "2026-09-01T00:00:00".
            return LocalDate.parse(texto.substring(0, Math.min(10, texto.length())));
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "La fecha '" + texto + "' no tiene un formato válido. "
                            + "Se espera AAAA-MM-DD.");
        }
    }

    // ===================== ERRORES =====================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> manejarValidacion(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    /**
     * Las reglas del servicio lanzan excepciones con mensajes escritos para
     * que los lea una persona ("el folio F6952 ya está registrado en un
     * pago"). Sin este manejador saldrían como 500 SIN cuerpo y el
     * navegador pintaría su texto genérico, que no dice nada útil.
     *
     * Cuando la excepción no trae mensaje (un NullPointer, por ejemplo) NO
     * se inventa una causa: se manda un texto neutro que manda a revisar
     * la consola.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> manejarReglaDeNegocio(RuntimeException ex) {
        String mensaje = ex.getMessage();
        if (mensaje == null || mensaje.isBlank()) {
            mensaje = "No se pudo completar la operación. "
                    + "Revisa la consola del servidor para ver el detalle.";
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mensaje);
    }
}