package com.cjv.sistemacjv.repository;

import com.cjv.sistemacjv.entity.Paquete;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaqueteRepository extends JpaRepository<Paquete, Integer> {

    Optional<Paquete> findByNombrePaquete(String nombrePaquete);
}