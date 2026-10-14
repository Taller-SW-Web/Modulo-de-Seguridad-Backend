package com.marketplace.auth_service.usuario.adapter.in.web;

import com.marketplace.auth_service.usuario.adapter.in.web.dto.ListarUsuariosRequest;
import com.marketplace.auth_service.usuario.adapter.in.web.dto.PaginaUsuariosResponse;
import com.marketplace.auth_service.usuario.adapter.in.web.dto.UsuarioResponse;
import com.marketplace.auth_service.usuario.adapter.in.web.mapper.UsuarioWebMapper;
import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.usuario.application.dto.FiltroCuenta;
import com.marketplace.auth_service.usuario.application.dto.Paginacion;
import com.marketplace.auth_service.usuario.application.dto.PaginaResultado;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {

    private final CuentasPort cuentasPort;

    public UsuarioController(CuentasPort cuentasPort) {
        this.cuentasPort = cuentasPort;
    }

    @GetMapping
    public ResponseEntity<PaginaUsuariosResponse> listarUsuarios(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String rol,
            @RequestParam(required = false) String correo,
            @RequestParam(defaultValue = "1") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {

        FiltroCuenta filtro = new FiltroCuenta(estado, rol, correo);
        Paginacion paginacion = new Paginacion(pagina, tamano);

        PaginaResultado<CuentaSnapshot> resultado = cuentasPort.consultar(filtro, paginacion);
        return ResponseEntity.ok(UsuarioWebMapper.toPageResponse(resultado));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponse> obtenerUsuario(@PathVariable UUID id) {
        Optional<CuentaSnapshot> snapshot = cuentasPort.leer(id);
        return snapshot.map(UsuarioWebMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}