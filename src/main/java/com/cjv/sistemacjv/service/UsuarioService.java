package com.cjv.sistemacjv.service;

import com.cjv.sistemacjv.entity.Usuario;
import com.cjv.sistemacjv.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Usuario> listarUsuarios() {
        return usuarioRepository.findAll();
    }

    // Lista solo las vendedoras activas (para el dropdown del modal de O.T.)
    public List<Usuario> listarVendedoras() {
        return usuarioRepository.findByEsVendedoraTrueAndActivoTrueOrderByNombreUsuarioAsc();
    }

    public Optional<Usuario> buscarPorId(Integer id) {
        return usuarioRepository.findById(id);
    }

    public Usuario guardarUsuario(Usuario usuario) {
        usuario.setContrasena(passwordEncoder.encode(usuario.getContrasena()));
        return usuarioRepository.save(usuario);
    }

    public Optional<Usuario> actualizarUsuario(Integer id, Usuario datosUsuario) {
        return usuarioRepository.findById(id).map(usuario -> {
            usuario.setRol(datosUsuario.getRol());
            usuario.setNombreUsuario(datosUsuario.getNombreUsuario());
            usuario.setCorreo(datosUsuario.getCorreo());
            usuario.setPorcentajeComision(datosUsuario.getPorcentajeComision());
            usuario.setEsVendedora(datosUsuario.getEsVendedora());
            usuario.setActivo(datosUsuario.getActivo());

            String nuevaContrasena = datosUsuario.getContrasena();
            if (nuevaContrasena != null && !nuevaContrasena.isBlank()) {
                usuario.setContrasena(passwordEncoder.encode(nuevaContrasena));
            }

            return usuarioRepository.save(usuario);
        });
    }

    public boolean eliminarUsuario(Integer id) {
        if (usuarioRepository.existsById(id)) {
            usuarioRepository.deleteById(id);
            return true;
        }
        return false;
    }

    public Optional<Usuario> verificarLogin(String nombreUsuario, String contrasena) {
        Optional<Usuario> usuarioEncontrado = usuarioRepository.findByNombreUsuario(nombreUsuario);

        if (usuarioEncontrado.isPresent()) {
            Usuario usuario = usuarioEncontrado.get();
            if (passwordEncoder.matches(contrasena, usuario.getContrasena())) {
                return Optional.of(usuario);
            }
        }

        return Optional.empty();
    }
}