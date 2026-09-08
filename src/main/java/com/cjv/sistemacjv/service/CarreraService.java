package com.cjv.sistemacjv.service;

import com.cjv.sistemacjv.entity.Carrera;
import com.cjv.sistemacjv.repository.CarreraRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CarreraService {

    private final CarreraRepository carreraRepository;

    public CarreraService(CarreraRepository carreraRepository) {
        this.carreraRepository = carreraRepository;
    }

    public List<Carrera> listarCarreras() {
        return carreraRepository.findAll();
    }

    public Optional<Carrera> buscarPorId(Integer id) {
        return carreraRepository.findById(id);
    }

    public Carrera guardarCarrera(Carrera carrera) {
        return carreraRepository.save(carrera);
    }

    public Optional<Carrera> actualizarCarrera(Integer id, Carrera datosCarrera) {
        return carreraRepository.findById(id).map(carrera -> {
            carrera.setEscuela(datosCarrera.getEscuela());
            carrera.setNombreCarrera(datosCarrera.getNombreCarrera());
            carrera.setActivo(datosCarrera.getActivo());
            return carreraRepository.save(carrera);
        });
    }

    public boolean eliminarCarrera(Integer id) {
        if (carreraRepository.existsById(id)) {
            carreraRepository.deleteById(id);
            return true;
        }
        return false;
    }
}