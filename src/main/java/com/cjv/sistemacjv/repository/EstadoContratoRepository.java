package com.cjv.sistemacjv.repository;

import com.cjv.sistemacjv.entity.EstadoContrato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EstadoContratoRepository extends JpaRepository<EstadoContrato, Integer> {

    Optional<EstadoContrato> findByNombreEstado(String nombreEstado);
}