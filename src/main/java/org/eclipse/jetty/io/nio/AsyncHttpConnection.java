package org.eclipse.jetty.io.nio;

import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.server.AbstractHttpConnection;

/**
 * 代表一个http连接连接
 */
public class AsyncHttpConnection extends AbstractHttpConnection {
    private HttpParser httpParser;
    private SelectChannelEndPoint endPoint;

    public AsyncHttpConnection(SelectChannelEndPoint endPoint){
        this.endPoint = endPoint;
        this.httpParser = new HttpParser(endPoint);
    }

    /**
     * http://www.west999.com/cms/wiki/code/2018-07-20/41829.html-长连接坑
     * @return
     */
    @Override
    public Connection handle(){
        Connection connection = this;
        boolean progress = true; //是否要继续处理

        endPoint.setCheckForIdle(false); //处理时无需检查空闲
        //每个连接只处理一次
        while (progress && connection == this){
            progress = false;


        }

        return connection;
    }
}
