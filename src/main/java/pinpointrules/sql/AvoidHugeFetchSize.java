package pinpointrules.sql;

import org.apache.openjpa.persistence.OpenJPAPersistence;
import org.apache.openjpa.persistence.OpenJPAQuery;
import org.apache.openjpa.persistence.jdbc.JDBCFetchPlan;
import org.hibernate.Query;
import org.hibernate.Session;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by BorgersJM on 9-6-2016.
 */
public class AvoidHugeFetchSize {

    private static final int FETCH_SIZE_HUGE = 1000;
    private static final int FETCH_SIZE_LARGE = 100;

    /**
     * From O
     * @param itg
     * @param newStatus
     */
    public void sendStatusUpdateinitiationForOutgoingIce(Object itg, String newStatus) {
        Object param = "outgoingIceKey" +  ", itg.getEntityKey()";
        List params = new ArrayList(1);
        params.add(param);
        Criteria c = new Criteria("NAMED_QUERY_WORK_KEYS_TO_STATUS_UPDATE", params);

        c.setFetchSize(FETCH_SIZE_HUGE); // should violate
        c.setFetchSize(501); // should violate

        c.setFetchSize(FETCH_SIZE_LARGE); // ok
        c.setFetchSize(80); // ok

        //EntityIterator iter = workDao.createEntityIterator(c, EntityIterator.EVICT_ON_NEXT);
    }

    public void jdbcFetchSize() {
        Statement statement = new Statement();

        statement.setFetchSize(FETCH_SIZE_LARGE); // ok
        statement.setFetchSize(80); // ok

        statement.setFetchSize(600); // should violate
        statement.setFetchSize(FETCH_SIZE_HUGE); // should violate

        PreparedStatement prepStatmt = new PreparedStatement();
        prepStatmt.setFetchSize(1000); // should violate

        int huge = 10000;
        prepStatmt.setFetchSize(huge); // should violate
    }

    public List hibernateFetchSize() {
        Session session = null;
        Query q = session.getNamedQuery("yourQuery");
        q.setFirstResult(20);
        q.setMaxResults(30);
        q.setFetchSize(100); // ok
        q.setFetchSize(1000); // should violate
        q.setFetchSize(FETCH_SIZE_HUGE); // should violate
        return q.list();
    }

    public Object findInformationByCode(String code) {
        EntityManager entityManager = null;
        javax.persistence.Query q = entityManager.createQuery("SELECT reas FROM Information info WHERE reas.code = :code", Object.class);
        q.setParameter("code", code);
        q.setMaxResults(1); //added JB
        q.setHint("org.hibernate.fetchSize", "1000"); // added JB option 1 - should violate
        q.setHint("org.hibernate.fetchSize", FETCH_SIZE_HUGE); // added JB option 1 - should violate
        //Session session = entityManager.unwrap(Session.class); // then you create the query from this session

		OpenJPAQuery ojpaQuery = OpenJPAPersistence.cast(q);
		JDBCFetchPlan plan = (JDBCFetchPlan) ojpaQuery.getFetchPlan();
        plan.setFetchBatchSize(10); //ok
		plan.setFetchBatchSize(1000); // violation
        plan.setFetchBatchSize(FETCH_SIZE_HUGE); // violation
        List results = q.getResultList();
        return null;
    }
    class Criteria {
        Criteria(Object namedQuery, Object params) {
        }
        void setFetchSize(int size) {

        }
    }

    class Statement {
        void setFetchSize(int size) {
        }
    }
    class PreparedStatement extends Statement {

    }
}
