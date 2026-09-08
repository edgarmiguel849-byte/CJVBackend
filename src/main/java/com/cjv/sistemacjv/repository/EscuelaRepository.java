package com.cjv.sistemacjv.repository;

import com.cjv.sistemacjv.entity.Escuela;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EscuelaRepository extends JpaRepository<Escuela, Integer> {
}