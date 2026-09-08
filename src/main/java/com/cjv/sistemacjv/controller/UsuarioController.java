package com.cjv.sistemacjv.controller;

import com.cjv.sistemacjv.entity.Usuario;
import com.cjv.sistemacjv.service.UsuarioService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    // Solo Jefe: administración de usuarios
    @GetMapping
    @PreAuthorize("hasRole('JEFE')")
    public List<Usuario> listarUsuarios() {
        return usuarioService.listarUsuarios();
    }

    // Solo Jefe
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('JEFE')")
    public ResponseEntity<Usuario> buscarUsuarioPorId(@PathVariable Integer id) {
        return usuarioService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Solo Jefe
    @PostMapping
    @PreAuthorize("hasRole('JEFE')")
    public Usuario guardarUsuario(@RequestBody Usuario usuario) {
        return usuarioService.guardarUsuario(usuario);
    }

    // Solo Jefe
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('JEFE')")
    public ResponseEntity<Usuario> actualizarUsuario(
            @PathVariable Integer id,
            @RequestBody Usuario datosUsuario
    ) {
        return usuarioService.actualizarUsuario(id, datosUsuario)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Solo Jefe
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('JEFE')")
    public ResponseEntity<Void> eliminarUsuario(@PathVariable Integer id) {
        boolean eliminado = usuarioService.eliminarUsuario(id);

        if (eliminado) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.notFound().build();
    }

    // Lista de vendedoras para el dropdown del modal de O.T.
    // Accesible para cualquier usuario autenticado (Jefe, Administrador o Mostrador),
    // porque los tres roles pueden crear O.T.
    @GetMapping("/vendedoras")
    public List<Usuario> listarVendedoras() {
        return usuarioService.listarVendedoras();
    }

    /**
     * Lista de jefes para el selector de "quién autoriza" una devolución (RN-11).
     *
     * Sin @PreAuthorize a propósito: el mostrador es quien captura la
     * devolución y necesita ver los nombres. Por eso devuelve SOLO
     * id y nombre, en vez del usuario completo con su correo y su
     * contraseña encriptada.
     */
    @GetMapping("/jefes")
    public List<Map<String, Object>> listarJefes() {
        return usuarioService.listarUsuarios().stream()
                .filter(u -> u.getRol() != null
                        && "Jefe".equalsIgnoreCase(u.getRol().getNombreRol()))
                .filter(u -> !Boolean.FALSE.equals(u.getActivo()))
                .map(u -> {
                    Map<String, Object> jefe = new HashMap<>();
                    jefe.put("idUsuario", u.getIdUsuario());
                    jefe.put("nombreUsuario", u.getNombreUsuario());
                    return jefe;
                })
                .toList();
    }
}