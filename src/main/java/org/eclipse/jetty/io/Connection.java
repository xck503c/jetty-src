package org.eclipse.jetty.io;

import java.io.IOException;

/**
 * https://github.com/eclipse/jetty.project/blob/jetty-8.1.x/jetty-io/src/main/java/org/eclipse/jetty/io/Connection.java
 * 代表以一个连接
 * 1. 当连接上有工作要做的时候，会调用handle方法进行处理；
 * 2. 对于阻塞连接，只要connection是打开的，就会一直调用；而非阻塞连接来说，只有当需要要读取，或者
 * 写阻塞变成可写之后，才会调用
 */
public interface Connection {
    /**
     * 处理
     * @return 返回下一要处理的连接
     * @throws IOException
     */
    Connection handle() throws IOException;

    /**
     * 返回连接创建时间戳
     */
    long getTimeStamp();

    /**
     * 连接是否空闲，不解析也不生成
     */
    boolean isIdle();

    /**
     * 是否对读取有兴趣
     * 名称有问题要改为isReadInterested()
     * @return
     */
    boolean isSuspended();

    /**
     * Called after the connection is closed
     */
    void onClose();

    /**
     * 连接超时之后调用，关闭连接
     * @param idleForMs
     */
    void onIdleExpired(long idleForMs);
}
