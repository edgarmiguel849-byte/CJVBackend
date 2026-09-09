package com.cjv.sistemacjv.controller;

import com.cjv.sistemacjv.dto.CorteDePersonaDTO;
import com.cjv.sistemacjv.entity.CierreCaja;
import com.cjv.sistemacjv.entity.Usuario;
import com.cjv.sistemacjv.repository.UsuarioRepository;
import com.cjv.sistemacjv.service.CierreCajaService;
import com.cjv.sistemacjv.service.ComisionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/cierres-caja")
public class CierreCajaController {

    private final CierreCajaService cierreCajaService;
    private final ComisionService comisionService;
    private final UsuarioRepository usuarioRepository;

    public CierreCajaController(CierreCajaService cierreCajaService,
                                ComisionService comisionService,
                                UsuarioRepository usuarioRepository) {
        this.cierreCajaService = cierreCajaService;
        this.comisionService = comisionService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping
    @PreAuthorize("hasRole('JEFE')")
    public List<CierreCaja> listarCierresCaja() {
        return cierreCajaService.listarCierresCaja();
    }

    /** Historial del más reciente al más viejo. Solo el Jefe. */
    @GetMapping("/historial")
    @PreAuthorize("hasRole('JEFE')")
    public List<CierreCaja> listarHistorial() {
        return cierreCajaService.listarHistorial();
    }

    /**
     * TODOS los cortes de un día, cada uno con su dueño y su estado.
     *
     * Es la vista del Jefe: un día puede tener el de Tete entregado, el de
     * Adri abierto y dos del Administrativo. Reemplaza a /por-fecha, que
     * solo alcanzaba a devolver uno.
     */
    @GetMapping("/del-dia")
    @PreAuthorize("hasRole('JEFE')")
    public List<CierreCaja> listarDelDia(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha
    ) {
        return cierreCajaService.listarPorFecha(fecha);
    }

    /**
     * Lo que YO tengo pendiente de entregar de ese día: mis movimientos que
     * todavía no viajan en ningún corte, con el efectivo que debería haber
     * en mi cajón.
     *
     * Es la vista previa de lo que voy a firmar. No guarda nada; solo
     * calcula. Cada quien ve el suyo, sin parámetro de usuario: sale del
     * token, así que nadie puede pedir el de otro cambiando la dirección.
     */
    @GetMapping("/mi-corte")
    public CorteDePersonaDTO miCorte(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha
    ) {
        Usuario yo = obtenerUsuarioLogueado();
        return comisionService.generarCorteDePersona(
                fecha, yo.getIdUsuario(), yo.getNombreUsuario());
    }

    /** ¿Ya entregué mi corte de ese día? Para que la pantalla sepa qué pintar. */
    @GetMapping("/ya-entregue")
    public boolean yaEntregue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha
    ) {
        Usuario yo = obtenerUsuarioLogueado();
        return cierreCajaService.yaEntregoSuCorte(fecha, yo.getIdUsuario());
    }

    /**
     * OJO - PROVISIONAL. Devuelve el PRIMER corte del día.
     *
     * Con un solo corte al día se comporta como siempre, pero con varios
     * MIENTE: le enseña a Adri el corte de Tete. Sigue aquí nada más para
     * que la pantalla vieja no truene mientras se rehace (3d-3).
     *
     * Cuando la pantalla nueva esté lista, se borra junto con
     * CierreCajaService.buscarPorFecha().
     */
    @Deprecated
    @GetMapping("/por-fecha")
    public ResponseEntity<CierreCaja> buscarPorFecha(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha
    ) {
        return cierreCajaService.buscarPorFecha(fecha)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * Entrego MI corte con el efectivo que conté.
     * El servidor recalcula lo esperado; el navegador solo manda lo contado.
     *
     * El candado de rol vive en el servicio (el Jefe no entrega corte), pero
     * aquí se filtra de una vez quién puede siquiera llamar al endpoint.
     * Antes NO tenía ninguno: cualquiera con sesión podía mandar un corte.
     */
    @PostMapping("/enviar")
    @PreAuthorize("hasAnyRole('MOSTRADOR','ADMINISTRADOR')")
    public CierreCaja enviar(@RequestBody EnvioCierreRequest peticion) {
        return cierreCajaService.enviar(
                peticion.getFecha(),
                peticion.getEfectivoContado(),
                peticion.getComentarios()
        );
    }

    /** Solo el Jefe. */
    @PutMapping("/{id}/autorizar")
    @PreAuthorize("hasRole('JEFE')")
    public CierreCaja autorizar(@PathVariable Integer id) {
        return cierreCajaService.autorizar(id);
    }

    /** Solo el Jefe. Devuelve un corte entregado a estado REABIERTO. */
    @PutMapping("/{id}/reabrir")
    @PreAuthorize("hasRole('JEFE')")
    public CierreCaja reabrir(@PathVariable Integer id) {
        return cierreCajaService.reabrir(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('JEFE')")
    public ResponseEntity<Void> eliminarCierreCaja(@PathVariable Integer id) {
        boolean eliminado = cierreCajaService.eliminarCierreCaja(id);

        if (eliminado) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.notFound().build();
    }

    /**
     * VA HASTA ABAJO A PROPÓSITO, no lo muevas para arriba.
     *
     * "/{id}" se traga CUALQUIER texto que venga después de la diagonal.
     * Spring resuelve por orden de declaración, así que con esta ruta
     * arriba, una petición a /mi-corte cae AQUÍ e intenta convertir
     * "mi-corte" a Integer -> 400 "For input string: mi-corte".
     *
     * Regla: las rutas con nombre fijo van ANTES que las de comodín.
     */
    @GetMapping("/{id}")
    public ResponseEntity<CierreCaja> buscarCierreCajaPorId(@PathVariable Integer id) {
        return cierreCajaService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Red de seguridad: convierte errores de validación en 400 con mensaje
     * legible, en lugar de un 500 feo.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> manejarValidacion(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
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
     * Lo único que el navegador tiene permitido mandar: la fecha, el efectivo
     * contado y el comentario. Todo lo demás lo calcula el servidor.
     */
    public static class EnvioCierreRequest {

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate fecha;

        private BigDecimal efectivoContado;
        private String comentarios;

        public LocalDate getFecha() {
            return fecha;
        }

        public void setFecha(LocalDate fecha) {
            this.fecha = fecha;
        }

        public BigDecimal getEfectivoContado() {
            return efectivoContado;
        }

        public void setEfectivoContado(BigDecimal efectivoContado) {
            this.efectivoContado = efectivoContado;
        }

        public String getComentarios() {
            return comentarios;
        }

        public void setComentarios(String comentarios) {
            this.comentarios = comentarios;
        }
    }
}