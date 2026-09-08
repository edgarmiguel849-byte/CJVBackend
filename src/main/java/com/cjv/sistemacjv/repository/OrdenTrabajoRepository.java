package com.cjv.sistemacjv.repository;

import com.cjv.sistemacjv.entity.OrdenTrabajo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrdenTrabajoRepository extends JpaRepository<OrdenTrabajo, Integer> {

    // Para validar el UNIQUE (numero, anio) antes de guardar
    Optional<OrdenTrabajo> findByNumeroAndAnio(Integer numero, Integer anio);

    // Listado normal: solo activas, ordenadas por año desc y número desc
    // (las más recientes primero, como 44/26 antes que 12/25)
    List<OrdenTrabajo> findByActivoTrueOrderByAnioDescNumeroDesc();

    // Buscador por carrera: ahora la carrera es texto libre en la propia O.T.
    // (columna carrera_texto), ya no una tabla aparte.
    List<OrdenTrabajo> findByActivoTrueAndCarreraTextoContainingIgnoreCaseOrderByAnioDescNumeroDesc(String carreraTexto);
}