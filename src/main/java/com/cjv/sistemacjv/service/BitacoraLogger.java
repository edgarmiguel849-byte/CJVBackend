package com.cjv.sistemacjv.service;

import com.cjv.sistemacjv.entity.Bitacora;
import com.cjv.sistemacjv.entity.Usuario;
import com.cjv.sistemacjv.repository.BitacoraRepository;
import com.cjv.sistemacjv.repository.UsuarioRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class BitacoraLogger {

    private final BitacoraRepository bitacoraRepository;
    private final UsuarioRepository usuarioRepository;

    public BitacoraLogger(BitacoraRepository bitacoraRepository,
                          UsuarioRepository usuarioRepository) {
        this.bitacoraRepository = bitacoraRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Registra una entrada en la bitácora con el usuario del token.
     * Si no hay usuario logueado (ej. tarea automática), no falla:
     * simplemente omite el registro y no rompe la operación principal.
     */
    public void registrar(String tablaAfectada,
                          Integer idRegistroAfectado,
                          String accion,
                          String descripcion) {
        try {
            Usuario usuario = obtenerUsuarioLogueado();
            if (usuario == null) {
                return; // Sin usuario logueado, no registramos y no fallamos.
            }

            Bitacora entrada = new Bitacora();
            entrada.setTablaAfectada(tablaAfectada);
            entrada.setIdRegistroAfectado(idRegistroAfectado);
            entrada.setAccion(accion);
            entrada.setDescripcion(descripcion);
            entrada.setUsuario(usuario);

            bitacoraRepository.save(entrada);
        } catch (Exception e) {
            // La bitácora nunca debe romper la operación principal.
            System.err.println("Error al registrar en bitácora: " + e.getMessage());
        }
    }

    private Usuario obtenerUsuarioLogueado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }

        String nombreUsuario = auth.getName();
        return usuarioRepository.findByNombreUsuario(nombreUsuario).orElse(null);
    }
}