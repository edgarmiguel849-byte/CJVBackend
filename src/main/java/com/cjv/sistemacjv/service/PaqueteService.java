package com.cjv.sistemacjv.service;

import com.cjv.sistemacjv.entity.Paquete;
import com.cjv.sistemacjv.repository.PaqueteRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PaqueteService {

    private final PaqueteRepository paqueteRepository;

    public PaqueteService(PaqueteRepository paqueteRepository) {
        this.paqueteRepository = paqueteRepository;
    }

    public List<Paquete> listarPaquetes() {
        return paqueteRepository.findAll();
    }

    public Optional<Paquete> buscarPorId(Integer id) {
        return paqueteRepository.findById(id);
    }

    public Paquete guardarPaquete(Paquete paquete) {
        return paqueteRepository.save(paquete);
    }

    public Optional<Paquete> actualizarPaquete(Integer id, Paquete datosPaquete) {
        return paqueteRepository.findById(id).map(paquete -> {
            paquete.setNombrePaquete(datosPaquete.getNombrePaquete());
            paquete.setDescripcion(datosPaquete.getDescripcion());
            paquete.setPrecio(datosPaquete.getPrecio());
            paquete.setActivo(datosPaquete.getActivo());
            return paqueteRepository.save(paquete);
        });
    }

    public boolean eliminarPaquete(Integer id) {
        if (paqueteRepository.existsById(id)) {
            paqueteRepository.deleteById(id);
            return true;
        }
        return false;
    }
}