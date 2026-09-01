package br.com.gestpro.nota.repository;

import br.com.gestpro.empresa.model.Empresa;
import br.com.gestpro.nota.model.FiscalAuditLog;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository @RequiredArgsConstructor
public class FiscalAuditStore {
    private final EntityManager entityManager;

    public void bloquearEmpresa(Long empresaId) {
        if (entityManager.find(Empresa.class, empresaId, LockModeType.PESSIMISTIC_WRITE) == null)
            throw new IllegalArgumentException("Empresa inexistente para auditoria fiscal.");
    }
    public Optional<FiscalAuditLog> ultimo(Long empresaId) {
        return entityManager.createQuery("select a from FiscalAuditLog a where a.empresaId=:empresaId order by a.id desc", FiscalAuditLog.class)
                .setParameter("empresaId", empresaId).setMaxResults(1).getResultStream().findFirst();
    }
    public void adicionar(FiscalAuditLog log) { entityManager.persist(log); }
}
