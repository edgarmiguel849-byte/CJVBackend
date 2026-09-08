package com.cjv.sistemacjv.service;

import com.cjv.sistemacjv.entity.EstadoContrato;
import com.cjv.sistemacjv.repository.EstadoContratoRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EstadoContratoService {

    private final EstadoContratoRepository estadoContratoRepository;

    public EstadoContratoService(EstadoContratoRepository estadoContratoRepository) {
        this.estadoContratoRepository = estadoContratoRepository;
    }

    public List<EstadoContrato> listarEstadosContrato() {
        return estadoContratoRepository.findAll();
    }

    public Optional<EstadoContrato> buscarPorId(Integer id) {
        return estadoContratoRepository.findById(id);
    }

    public EstadoContrato guardarEstadoContrato(EstadoContrato estadoContrato) {
        return estadoContratoRepository.save(estadoContrato);
    }

    public Optional<EstadoContrato> actualizarEstadoContrato(Integer id, EstadoContrato datosEstadoContrato) {
        return estadoContratoRepository.findById(id).map(estadoContrato -> {
            estadoContrato.setNombreEstado(datosEstadoContrato.getNombreEstado());
            estadoContrato.setActivo(datosEstadoContrato.getActivo());
            return estadoContratoRepository.save(estadoContrato);
        });
    }

    public boolean eliminarEstadoContrato(Integer id) {
        if (estadoContratoRepository.existsById(id)) {
            estadoContratoRepository.deleteById(id);
            return true;
        }
        return false;
    }
}