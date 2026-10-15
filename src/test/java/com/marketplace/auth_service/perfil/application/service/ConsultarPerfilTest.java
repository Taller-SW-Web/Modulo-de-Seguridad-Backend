package com.marketplace.auth_service.perfil.application.service;

import static com.marketplace.auth_service.perfil.application.service.DoblesPerfil.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.auth_service.perfil.application.dto.DocumentoProyectado;
import com.marketplace.auth_service.perfil.application.dto.PerfilProyectado;
import com.marketplace.auth_service.perfil.domain.model.CuentaPerfilNoEncontradaException;
import com.marketplace.auth_service.perfil.domain.model.DocumentoNoLegibleException;
import com.marketplace.auth_service.perfil.domain.model.DocumentoProtegido;
import com.marketplace.auth_service.perfil.domain.model.PerfilCliente;
import com.marketplace.auth_service.perfil.domain.model.PerfilVendedor;
import com.marketplace.auth_service.perfil.domain.model.ProyeccionNoAutorizadaException;
import com.marketplace.auth_service.perfil.domain.model.TipoDocumento;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConsultarPerfilTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final DocumentoProtegido CIFRADO =
            new DocumentoProtegido(TipoDocumento.DNI, "ZZZ-CIFRADO-999", "k1", "hmac");

    private static ConsultarPerfil servicio(DoblesPerfil d, String enClaro) {
        return new ConsultarPerfil(d, d, d, d, d, new ProyectorDocumento(documento -> enClaro));
    }

    @Test
    void leerPropioClienteConDocumentoEnmascaradoYSinAtributosDeVendedor() {
        DoblesPerfil d = new DoblesPerfil();
        d.cuenta(A, "Ana", "Perez", "+51987654321", Set.of("CLIENTE"), false);
        d.clientes.put(A, new PerfilCliente(A, LocalDate.of(1999, 5, 4), CIFRADO));

        PerfilProyectado p = servicio(d, "12345234").leerPropio(actor(A));

        assertThat(p.id()).isEqualTo(A);
        assertThat(p.roles()).containsExactly("CLIENTE");
        assertThat(p.atributosCliente().fechaNacimiento()).isEqualTo(LocalDate.of(1999, 5, 4));
        assertThat(p.atributosVendedor()).isNull();
        assertThat(p.documento()).isEqualTo(new DocumentoProyectado("DNI", "*****234"));
        assertThat(p.toString()).doesNotContain("12345234").doesNotContain("ZZZ-CIFRADO-999");
    }

    @Test
    void leerPropioUsaElSubDelActorYNoOtroId() {
        DoblesPerfil d = new DoblesPerfil();
        d.cuenta(A, "Ana", "Perez", "+51987654321", Set.of("CLIENTE"), false);
        d.cuenta(B, "Beto", "Diaz", "+51922222222", Set.of("CLIENTE"), false);

        assertThat(servicio(d, "x").leerPropio(actor(A)).id()).isEqualTo(A);
    }

    @Test
    void sinPerfilRegistradoNoInventaValoresYDocumentoQuedaNull() {
        DoblesPerfil d = new DoblesPerfil();
        d.cuenta(A, "Ana", "Perez", "+51987654321", Set.of("CLIENTE", "VENDEDOR"), false);

        PerfilProyectado p = servicio(d, "x").leerPropio(actor(A));

        assertThat(p.atributosCliente().fechaNacimiento()).isNull();
        assertThat(p.atributosVendedor().codigoVendedor()).isNull();
        assertThat(p.atributosVendedor().tienda()).isNull();
        assertThat(p.atributosVendedor().fechaIngreso()).isNull();
        assertThat(p.documento()).isNull();
        assertThat(p.roles()).containsExactly("CLIENTE", "VENDEDOR");
    }

    @Test
    void rolesMultiplesMuestranAmbosConjuntos() {
        DoblesPerfil d = new DoblesPerfil();
        d.cuenta(A, "Ana", "Perez", "+51987654321", Set.of("CLIENTE", "VENDEDOR"), false);
        d.clientes.put(A, new PerfilCliente(A, LocalDate.of(2000, 1, 1), null));
        d.vendedores.put(A, new PerfilVendedor(A, "VEND-1042", "TIENDA-MIRAFLORES", LocalDate.of(2024, 3, 1)));

        PerfilProyectado p = servicio(d, "x").leerPropio(actor(A));

        assertThat(p.atributosCliente().fechaNacimiento()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(p.atributosVendedor().codigoVendedor()).isEqualTo("VEND-1042");
        assertThat(p.atributosVendedor().tienda()).isEqualTo("TIENDA-MIRAFLORES");
    }

    @Test
    void cuentaDeGestionNoMuestraAtributosDeRolYMfaVieneDelPuerto() {
        DoblesPerfil d = new DoblesPerfil();
        d.cuenta(A, "Gina", "Rios", "+51911111111", Set.of("ADMIN_SISTEMA"), true);
        d.clientes.put(A, new PerfilCliente(A, LocalDate.of(1990, 1, 1), null));

        PerfilProyectado p = servicio(d, "x").leerPropio(actor(A));

        assertThat(p.mfaHabilitado()).isTrue();
        assertThat(p.atributosCliente()).isNull();
        assertThat(p.atributosVendedor()).isNull();
    }

    @Test
    void celularNoVerificadoSePropagaTalComoLoEntregaCuentas() {
        DoblesPerfil d = new DoblesPerfil();
        d.cuenta(A, "Ana", "Perez", "+51987654321", Set.of("CLIENTE"), false);
        d.actualizarBasicos(A, new com.marketplace.auth_service.perfil.application.dto.CambioAtributos(
                com.marketplace.auth_service.perfil.application.dto.CampoEditable.omitido(), com.marketplace.auth_service.perfil.application.dto.CampoEditable.omitido(),
                com.marketplace.auth_service.perfil.application.dto.CampoEditable.presente("+51900000000")));

        assertThat(servicio(d, "x").leerPropio(actor(A)).celularVerificado()).isFalse();
    }

    @Test
    void documentoIlegibleNoSeFingeAusencia() {
        DoblesPerfil d = new DoblesPerfil();
        d.cuenta(A, "Ana", "Perez", "+51987654321", Set.of("CLIENTE"), false);
        d.clientes.put(A, new PerfilCliente(A, null, CIFRADO));
        ConsultarPerfil servicio = new ConsultarPerfil(d, d, d, d, d, new ProyectorDocumento(documento -> {
            throw new DocumentoNoLegibleException("clave ausente");
        }));

        assertThatThrownBy(() -> servicio.leerPropio(actor(A))).isInstanceOf(DocumentoNoLegibleException.class);
    }

    @Test
    void cuentaInexistenteLanzaNoEncontrada() {
        DoblesPerfil d = new DoblesPerfil();
        assertThatThrownBy(() -> servicio(d, "x").leerPropio(actor(A))).isInstanceOf(CuentaPerfilNoEncontradaException.class);
    }

    @Test
    void proyectarParaOtroSinPermisoDenegaYNoLeeNada() {
        DoblesPerfil d = new DoblesPerfil();
        d.cuenta(B, "Beto", "Diaz", "+51922222222", Set.of("CLIENTE"), false);

        assertThatThrownBy(() -> servicio(d, "x").proyectarPara(actor(A), B)).isInstanceOf(ProyeccionNoAutorizadaException.class);
        assertThat(d.lecturas).isEqualTo(0);
    }

    @Test
    void proyectarParaConUsuarioVerDevuelveElObjetivo() {
        DoblesPerfil d = new DoblesPerfil();
        d.cuenta(B, "Beto", "Diaz", "+51922222222", Set.of("CLIENTE"), false);
        d.actoresConUsuarioVer.add(A);

        assertThat(servicio(d, "x").proyectarPara(actor(A), B).id()).isEqualTo(B);
    }

    @Test
    void proyectarParaElPropioIdFuncionaSinPrivilegio() {
        DoblesPerfil d = new DoblesPerfil();
        d.cuenta(A, "Ana", "Perez", "+51987654321", Set.of("CLIENTE"), false);
        assertThat(servicio(d, "x").proyectarPara(actor(A), A).id()).isEqualTo(A);
    }
}
