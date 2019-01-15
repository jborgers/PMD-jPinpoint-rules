package com.jpinpoint.perf.pinpointrules.concurrent;

import javax.servlet.http.HttpServlet;
import java.io.File;
import java.util.Collections;
import java.util.Set;

/**
 * @TODO
 */
public class AvoidExposingSyncWrappedInternal {
    private final Set<String> portletNames;

    public AvoidExposingSyncWrappedInternal(Set names) {
        //Set copy = new HashSet(names); // copy fixes the problem since Set is not shared anymore,
        // only the immutable Strings which are inherently thread-safe
        portletNames = Collections.synchronizedSet(names);
    }

    public void addPortletName(String name) {
        portletNames.add(name);
    }
}

class WorkServlet extends HttpServlet {

    private String workFileName = "<invalid>";

    public void setWorkFileName(String name) {
        workFileName = name;
    }

    public void sendWorkFromFile() {
        File file = new File(workFileName);

        //..send mq message
    }
}