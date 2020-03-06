package org.eclipse.jetty.io.main;

import org.eclipse.jetty.io.nio.SelectChannelConnector;

public class Main {
    public static void main(String[] args) throws Exception{
        SelectChannelConnector selectChannelConnector = new SelectChannelConnector();
        selectChannelConnector.doStart();
    }
}
