package com.cjv.sistemacjv.service;

import com.cjv.sistemacjv.entity.Escuela;
import com.cjv.sistemacjv.repository.EscuelaRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EscuelaService {

    private final EscuelaRepository escuelaRepository;

    public EscuelaService(EscuelaRepository escuelaRepository) {
        this.escuelaRepository = escuelaRepository;
    }

    public List<Escuela> listarEscuelas() {
        return escuelaRepository.findAll();
    }

    public Optional<Escuela> buscarPorId(Integer id) {
        return escuelaRepository.findById(id);
    }

    public Escuela guardarEscuela(Escuela escuela) {
        return escuelaRepository.save(escuela);
    }

    public Optional<Escuela> actualizarEscuela(Integer id, Escuela datosEscuela) {
        return escuelaRepository.findById(id).map(escuela -> {
            escuela.setNombreEscuela(datosEscuela.getNombreEscuela());
            return escuelaRepository.save(escuela);
        });
    }

    public boolean eliminarEscuela(Integer id) {
        if (escuelaRepository.existsById(id)) {
            escuelaRepository.deleteById(id);
            return true;
        }
        return false;
    }
}