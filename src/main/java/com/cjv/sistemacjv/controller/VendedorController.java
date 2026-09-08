package com.cjv.sistemacjv.controller;

import com.cjv.sistemacjv.entity.Vendedor;
import com.cjv.sistemacjv.service.VendedorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vendedores")
public class VendedorController {

    private final VendedorService vendedorService;

    public VendedorController(VendedorService vendedorService) {
        this.vendedorService = vendedorService;
    }

    // Lista todos (para la pantalla de administración)
    @GetMapping
    public List<Vendedor> listar() {
        return vendedorService.listarTodos();
    }

    // Solo activos (para el desplegable de O.T.)
    @GetMapping("/activos")
    public List<Vendedor> listarActivos() {
        return vendedorService.listarActivos();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Vendedor> buscarPorId(@PathVariable Integer id) {
        return vendedorService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Vendedor vendedor) {
        try {
            return ResponseEntity.ok(vendedorService.crear(vendedor));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Integer id, @RequestBody Vendedor datos) {
        try {
            return vendedorService.actualizar(id, datos)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Integer id) {
        return vendedorService.desactivar(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}