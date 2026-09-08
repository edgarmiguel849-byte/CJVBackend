package com.cjv.sistemacjv.repository;

import com.cjv.sistemacjv.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    Optional<Usuario> findByNombreUsuario(String nombreUsuario);

    // Para el dropdown de vendedora en el modal de O.T.
    List<Usuario> findByEsVendedoraTrueAndActivoTrueOrderByNombreUsuarioAsc();
}