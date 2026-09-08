package com.cjv.sistemacjv.service;

import com.cjv.sistemacjv.entity.Rol;
import com.cjv.sistemacjv.repository.RolRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class RolService {

    private final RolRepository rolRepository;

    public RolService(RolRepository rolRepository) {
        this.rolRepository = rolRepository;
    }

    public List<Rol> listarRoles() {
        return rolRepository.findAll();
    }

    public Optional<Rol> buscarPorId(Integer id) {
        return rolRepository.findById(id);
    }

    public Rol guardarRol(Rol rol) {
        return rolRepository.save(rol);
    }

    public Optional<Rol> actualizarRol(Integer id, Rol datosRol) {
        return rolRepository.findById(id).map(rol -> {
            rol.setNombreRol(datosRol.getNombreRol());
            rol.setActivo(datosRol.getActivo());
            return rolRepository.save(rol);
        });
    }

    public boolean eliminarRol(Integer id) {
        if (rolRepository.existsById(id)) {
            rolRepository.deleteById(id);
            return true;
        }
        return false;
    }
}