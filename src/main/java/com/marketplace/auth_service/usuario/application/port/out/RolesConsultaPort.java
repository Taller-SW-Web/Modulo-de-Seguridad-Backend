package com.marketplace.auth_service.usuario.application.port.out;

import java.util.Set;
import java.util.UUID;

public interface RolesConsultaPort { Set<UUID> usuariosConRol(String rol); }
