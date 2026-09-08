package com.cjv.sistemacjv.repository;

import com.cjv.sistemacjv.entity.Bitacora;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BitacoraRepository extends JpaRepository<Bitacora, Integer> {
}