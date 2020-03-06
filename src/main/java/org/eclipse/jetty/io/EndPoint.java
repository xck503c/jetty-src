package org.eclipse.jetty.io;

import java.io.IOException;

/**
 * https://github.com/eclipse/jetty.project/blob/jetty-8.1.x/jetty-io/src/main/java/org/eclipse/jetty/io/EndPoint.java
 * http://www.blogjava.net/DLevin/archive/2014/03/29/411666.html
 * 1. EndPoint是对一次客户端到服务端连接的抽象，每个新的连接都会创建一个；
 * 2. EndPoint会包含此次通信的socket连接，因而它主要用于处理从Socket中读取数据和向Socket中写入数据
 */
public interface EndPoint {

    void shutdownOutput() throws IOException;

    boolean isOutPutShutdown();

    void shutdownInput() throws IOException;

    boolean isInputShutdown();

    //关闭当前连接
    void close() throws IOException;

    /**
     * 从socket中读取数据，并写入buffer中，直到数据读取完成或者满了。
     * 返回读取的字节数
     */
    int fill(Buffer buffer) throws IOException;

    /**
     * 将Buffer中的数据写入到socket中，同时清除缓存
     */
    int flush(Buffer buffer) throws IOException;

    /**
     * 按照顺序写入socket中
     */
    int flush(Buffer header, Buffer buffer, Buffer trailer) throws IOException;

    String getLocalAddr();

    String getLocalHost();

    int getLocalPort();

    String getRemoteAddr();

    String getRemoteHost();

    int getRemotePort();

    //阻塞读写和判断
    boolean isBlocking();

    boolean blockReadable(long millisecs) throws IOException;

    boolean blockWritable(long millisecs) throws IOException;

    boolean isOpen();

    //获取底层传输对象
    Object getTransport();

    void flush() throws IOException;

    /**
     * 端点可以空闲的最大时间，大致对应于用于阻塞连接的getSoTimeout()
     */
    int getMaxIdleTime();

    void setMaxIdleTime(int timeMs) throws IOException;
}
