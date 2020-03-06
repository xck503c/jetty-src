package org.eclipse.jetty.io.nio;

import org.eclipse.jetty.io.Connection;

/**
 * 代表一个TCP连接
 */
public class AsyncHttpConnection implements Connection {
    private HttpParser httpParser;
    private SelectChannelEndPoint endPoint;

    public AsyncHttpConnection(SelectChannelEndPoint endPoint){
        this.endPoint = endPoint;
        httpParser = new HttpParser(endPoint);
    }

    @Override
    public Connection handle(){
        Connection connection = this;
        boolean progress = true; //是否要继续处理

        //每个连接只处理一次
        while (progress && connection == this){
            progress = false;


        }

        return connection;
    }
}
