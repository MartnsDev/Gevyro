package br.com.gestpro.nota;

import java.util.EnumSet;
import java.util.Set;

public enum FiscalRole {
    ADMINISTRADOR(EnumSet.allOf(FiscalPermission.class)),
    FISCAL(EnumSet.of(FiscalPermission.VISUALIZAR, FiscalPermission.EMITIR, FiscalPermission.CANCELAR,
            FiscalPermission.INUTILIZAR, FiscalPermission.CONFIGURAR)),
    OPERADOR(EnumSet.of(FiscalPermission.VISUALIZAR, FiscalPermission.EMITIR)),
    CONTADOR(EnumSet.of(FiscalPermission.VISUALIZAR, FiscalPermission.EXPORTAR)),
    SOMENTE_LEITURA(EnumSet.of(FiscalPermission.VISUALIZAR));

    private final Set<FiscalPermission> permissions;
    FiscalRole(Set<FiscalPermission> permissions) { this.permissions = Set.copyOf(permissions); }
    public boolean permite(FiscalPermission permission) { return permissions.contains(permission); }
}
