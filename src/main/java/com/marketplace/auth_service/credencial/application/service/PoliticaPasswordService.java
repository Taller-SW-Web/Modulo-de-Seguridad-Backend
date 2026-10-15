package com.marketplace.auth_service.credencial.application.service;

import com.marketplace.auth_service.credencial.application.dto.*;
import com.marketplace.auth_service.credencial.application.port.in.ConsultarPoliticaPort;
import com.marketplace.auth_service.credencial.application.port.out.*;
import com.marketplace.auth_service.credencial.domain.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import static com.marketplace.auth_service.credencial.application.dto.ReglaPassword.*;

public final class PoliticaPasswordService implements ConsultarPoliticaPort {
    private final PasswordComunesPort comunes;
    private final DatosPersonalesPasswordPort personas;
    private final PersistenciaCredencialesPort persistencia;
    private final PasswordHistoryChecker historial;

    public PoliticaPasswordService(PasswordComunesPort comunes, DatosPersonalesPasswordPort personas,
            PersistenciaCredencialesPort persistencia, PasswordHistoryChecker historial) {
        this.comunes = Objects.requireNonNull(comunes);
        this.personas = Objects.requireNonNull(personas);
        this.persistencia = Objects.requireNonNull(persistencia);
        this.historial = Objects.requireNonNull(historial);
    }
    @Override public PoliticaPassword consultar() { return PoliticaPassword.vigente(); }

    public ValidacionPassword validar(UUID usuarioId, SecretoEfimero secreto) {
        Objects.requireNonNull(usuarioId);
        String valor = secreto.valor();
        List<InfraccionPassword> errores = new ArrayList<>();
        if (valor.codePointCount(0, valor.length()) < 10) errores.add(new InfraccionPassword(LONGITUD_MINIMA, 10));
        exigir(errores, MAYUSCULA, valor.codePoints().anyMatch(Character::isUpperCase));
        exigir(errores, MINUSCULA, valor.codePoints().anyMatch(Character::isLowerCase));
        exigir(errores, DIGITO, valor.codePoints().anyMatch(Character::isDigit));
        exigir(errores, CARACTER_ESPECIAL, valor.codePoints().anyMatch(c ->
                !Character.isLetterOrDigit(c) && !Character.isWhitespace(c)));
        String copia = ComparacionPassword.normalizar(valor);
        exigir(errores, CONTRASENA_COMUN, !comunes.contiene(copia));
        DatosPersonalesPassword datos = personas.leer(usuarioId);
        exigir(errores, DATOS_PERSONALES, !contienePersonales(copia, datos));
        persistencia.leer(usuarioId).ifPresent(actual -> exigir(errores, YA_UTILIZADA,
                !historial.reutiliza(valor, actual, persistencia.historial(usuarioId, actual))));
        return new ValidacionPassword(errores);
    }

    private static void exigir(List<InfraccionPassword> errores, ReglaPassword regla, boolean cumple) {
        if (!cumple) errores.add(new InfraccionPassword(regla, null));
    }
    private static boolean contienePersonales(String copia, DatosPersonalesPassword datos) {
        for (String campo : List.of(datos.nombres(), datos.apellidos(), datos.correo(),
                datos.correo().split("@", 2)[0])) {
            String normalizado = ComparacionPassword.normalizar(campo);
            if (!normalizado.isEmpty() && copia.contains(normalizado)) return true;
        }
        for (String token : ComparacionPassword.normalizar(datos.nombres() + " " + datos.apellidos()).split(" ")) {
            if (token.codePointCount(0, token.length()) >= 3 && copia.contains(token)) return true;
        }
        return false;
    }
}
