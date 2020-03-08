package org.eclipse.jetty.http;

import org.eclipse.jetty.io.Buffers;

/**
 * http的抽象缓存池：有四类，请求头，请求内容，响应头，响应内容
 */
public interface HttpBuffers {
    /**
     * 请求内容缓冲区大小
     */
    public int getRequestBufferSize();

    public void setRequestBufferSize(int requestBufferSize);

    /**
     * 请求头缓冲区大小
     */
    public int getRequestHeaderSize();

    public void setRequestHeaderSize(int requestHeaderSize);

    /**
     * 响应内容缓冲区大小
     */
    public int getResponseBufferSize();

    public void setResponseBufferSize(int responseBufferSize);

    /**
     * 响应头缓冲区大小
     */
    public int getResponseHeaderSize();

    //大小和类型的setter和getter
    public void setResponseHeaderSize(int responseHeaderSize);

    public Buffers.Type getRequestBufferType();

    public Buffers.Type getRequestHeaderType();

    public Buffers.Type getResponseBufferType();

    public Buffers.Type getResponseHeaderType();

    public void setRequestBuffers(Buffers requestBuffers);

    public void setResponseBuffers(Buffers responseBuffers);

    public Buffers getRequestBuffers();

    public Buffers getResponseBuffers();

    //最大限制
    public void setMaxBuffers(int maxBuffers);

    public int getMaxBuffers();

}
