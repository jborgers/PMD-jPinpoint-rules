package com.jpinpoint.perf.pinpointrules.sql;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.util.List;

public class AvoidMultipleRoundtripsForQuery {

    @PersistenceContext(unitName = "rPersistenceUnit")
    protected EntityManager entityManager;

    public Object findInformationByCode(String code) {
        Query q = this.entityManager.createQuery("SELECT reas FROM Information reas WHERE reas.code = :code", Object.class);
        q.setParameter("code", code);
        List results = q.getResultList();
        if (results != null && !results.isEmpty()) {
            return (Object) q.getSingleResult(); // violation, 2nd query
        }
        return null;
    }

    public Object findInformationByCodeOk(String code) {
        Query q = this.entityManager.createQuery("SELECT reas FROM Information reas WHERE reas.code = :code", Object.class);
        q.setParameter("code", code);
        try {
            return (Object) q.getSingleResult(); // Ok, 1 query
        }
        catch(NoResultException e) {
            return null;
        }
    }
}

