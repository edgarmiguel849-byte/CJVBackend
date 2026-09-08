package com.cjv.sistemacjv.controller;

import com.cjv.sistemacjv.config.JwtUtil;
import com.cjv.sistemacjv.entity.Usuario;
import com.cjv.sistemacjv.service.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/login")
public class LoginController {

    private final UsuarioService usuarioService;
    private final JwtUtil jwtUtil;

    public LoginController(UsuarioService usuarioService, JwtUtil jwtUtil) {
        this.usuarioService = usuarioService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    public ResponseEntity<?> login(@RequestBody Map<String, String> credenciales) {
        String nombreUsuario = credenciales.get("nombreUsuario");
        String contrasena = credenciales.get("contrasena");

        Optional<Usuario> usuarioEncontrado = usuarioService.verificarLogin(nombreUsuario, contrasena);

        if (usuarioEncontrado.isPresent()) {
            Usuario usuario = usuarioEncontrado.get();

            if (Boolean.FALSE.equals(usuario.getActivo())) {
                Map<String, String> error = new HashMap<>();
                error.put("mensaje", "Este usuario está desactivado");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }

            String token = jwtUtil.generarToken(
                    usuario.getNombreUsuario(),
                    usuario.getRol().getNombreRol()
            );

            Map<String, Object> respuesta = new HashMap<>();
            respuesta.put("mensaje", "Login exitoso");
            respuesta.put("token", token);
            respuesta.put("usuario", usuario);
            return ResponseEntity.ok(respuesta);
        } else {
            Map<String, String> error = new HashMap<>();
            error.put("mensaje", "Credenciales incorrectas");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }
}