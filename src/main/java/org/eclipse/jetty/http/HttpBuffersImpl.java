package org.eclipse.jetty.http;

import org.eclipse.jetty.io.Buffers;
import org.eclipse.jetty.io.BuffersFactory;

public class HttpBuffersImpl {

    //四类缓冲区的大小默认值，单位字节
    private int requestBufferSize = 16*1024;
    private int requestHeaderSize = 6*1024;
    private int responseBufferSize = 32*1024;
    private int responseHeaderSize = 6*1024;
    //缓冲源中最大缓冲区数量限制
    private int maxBuffers = 1024;

    //默认都是字节数组类型
    private Buffers.Type _requestBufferType = Buffers.Type.BYTE_ARRAY;
    private Buffers.Type _requestHeaderType = Buffers.Type.BYTE_ARRAY;
    private Buffers.Type _responseBufferType = Buffers.Type.BYTE_ARRAY;
    private Buffers.Type _responseHeaderType = Buffers.Type.BYTE_ARRAY;

    //两类缓冲源，请求和响应
    private Buffers requestBuffers;
    private Buffers responseBuffers;

    public HttpBuffersImpl() {
        super();
    }

    public int getRequestBufferSize() {
        return requestBufferSize;
    }

    public void setRequestBufferSize(int requestBufferSize) {
        requestBufferSize = requestBufferSize;
    }

    public int getRequestHeaderSize() {
        return requestHeaderSize;
    }

    public void setRequestHeaderSize(int requestHeaderSize) {
        this.requestHeaderSize = requestHeaderSize;
    }

    public int getResponseBufferSize() {
        return responseBufferSize;
    }

    public void setResponseBufferSize(int responseBufferSize) {
        this.responseBufferSize = responseBufferSize;
    }

    public int getResponseHeaderSize() {
        return responseHeaderSize;
    }

    public void setResponseHeaderSize(int responseHeaderSize) {
        this.responseHeaderSize = responseHeaderSize;
    }

    public Buffers.Type getRequestBufferType() {
        return _requestBufferType;
    }

    public void setRequestBufferType(Buffers.Type requestBufferType) {
        _requestBufferType = requestBufferType;
    }

    public Buffers.Type getRequestHeaderType() {
        return _requestHeaderType;
    }

    public void setRequestHeaderType(Buffers.Type requestHeaderType) {
        _requestHeaderType = requestHeaderType;
    }

    public Buffers.Type getResponseBufferType() {
        return _responseBufferType;
    }

    public void setResponseBufferType(Buffers.Type responseBufferType) {
        _responseBufferType = responseBufferType;
    }

    public Buffers.Type getResponseHeaderType() {
        return _responseHeaderType;
    }

    public void setResponseHeaderType(Buffers.Type responseHeaderType) {
        _responseHeaderType = responseHeaderType;
    }

    public void setRequestBuffers(Buffers requestBuffers) {
        this.requestBuffers = requestBuffers;
    }

    public void setResponseBuffers(Buffers responseBuffers) {
        this.responseBuffers = responseBuffers;
    }

    protected void doStart() throws Exception {
        requestBuffers = BuffersFactory.newBuffers(_requestHeaderType,requestHeaderSize
                ,_requestBufferType,requestBufferSize,_requestBufferType,getMaxBuffers());
        responseBuffers = BuffersFactory.newBuffers(_responseHeaderType,responseHeaderSize
                ,_responseBufferType,responseBufferSize,_responseBufferType,getMaxBuffers());
    }

    protected void doStop() throws Exception {
        requestBuffers=null;
        responseBuffers=null;
    }

    public Buffers getRequestBuffers() {
        return requestBuffers;
    }

    public Buffers getResponseBuffers() {
        return responseBuffers;
    }

    public void setMaxBuffers(int maxBuffers) {
        maxBuffers = maxBuffers;
    }

    public int getMaxBuffers() {
        return maxBuffers;
    }

    public String toString() {
        return requestBuffers+"/"+responseBuffers;
    }
}
