package com.marketplace.auth_service.credencial.adapter.in.web;

import com.marketplace.auth_service.credencial.application.port.in.ConsultarPoliticaPort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PoliticaPasswordController {
    private final ConsultarPoliticaPort politica;
    public PoliticaPasswordController(ConsultarPoliticaPort politica) { this.politica = politica; }
    @GetMapping(path = "/api/v1/password/politica", produces = MediaType.APPLICATION_JSON_VALUE)
    public PoliticaPasswordResponse consultar() { return PoliticaPasswordWebMapper.aRespuesta(politica.consultar()); }
}
