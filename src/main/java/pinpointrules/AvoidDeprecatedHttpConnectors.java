package nl.rabobank.perf.pinpointrules;

import org.apache.commons.httpclient.SimpleHttpConnectionManager;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;

/*
deprecated: ClientConnectionManager, ThreadSafeClientConnManager and PoolingClientConnectionManager;

Risk of session data mixup: SimpleHttpConnectionManager

good: PoolingHttpClientConnectionManager

found also deprecated: ClientConnectionManager, SingleClientConnManager, DefaultHttpClient, SystemDefaultHttpClient
 */
public class AvoidDeprecatedHttpConnectors {

    private SimpleHttpConnectionManager simple = new SimpleHttpConnectionManager();
    ClientConnectionManager mgr = new ThreadSafeClientConnManager();

    private AvoidDeprecatedHttpConnectors() {
        new SimpleHttpConnectionManager();
    }

}
