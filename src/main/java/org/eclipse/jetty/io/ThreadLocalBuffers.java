package org.eclipse.jetty.io;

/**
 * 每个线程都会有缓存三种缓冲区，buffer和header不够会尝试从other拿，但是为什么获取
 * 指定大小的缓冲区不去尝试从前两个去拿
 */
public class ThreadLocalBuffers extends AbstractBuffers{

    //每个线程专属一个
    private final ThreadLocal<ThreadBuffers> threadBuffers = new ThreadLocal<ThreadBuffers>(){
        @Override
        protected ThreadBuffers initialValue(){
            return new ThreadBuffers();
        }
    };

    public ThreadLocalBuffers(Buffers.Type headerType, int headerSize, Buffers.Type bufferType, int bufferSize, Buffers.Type otherType) {
        super(headerType,headerSize,bufferType,bufferSize,otherType);
    }

    public Buffer getBuffer() {
        ThreadBuffers buffers = threadBuffers.get();
        if (buffers._buffer!=null) {
            Buffer b=buffers._buffer;
            buffers._buffer=null; //租借后置空引用
            return b;
        }

        //buffer租借不了就租借other
        if (buffers._other!=null && isBuffer(buffers._other)) {
            Buffer b=buffers._other;
            buffers._other=null;
            return b;
        }

        return newBuffer();
    }

    public Buffer getHeader()
    {
        ThreadBuffers buffers = threadBuffers.get();
        if (buffers._header!=null) {
            Buffer b=buffers._header;
            buffers._header=null;
            return b;
        }

        if (buffers._other!=null && isHeader(buffers._other)) {
            Buffer b=buffers._other;
            buffers._other=null;
            return b;
        }

        return newHeader();
    }

    /**
     * 只会从other中拿
     * @param size
     * @return
     */
    public Buffer getBuffer(int size) {
        ThreadBuffers buffers = threadBuffers.get();
        if (buffers._other!=null && buffers._other.capacity()==size) {
            Buffer b=buffers._other;
            buffers._other=null;
            return b;
        }

        return newBuffer(size);
    }

    public void returnBuffer(Buffer buffer) {
        buffer.clear();
        if (buffer.isVolatile() || buffer.isImmutable())
            return;

        ThreadBuffers buffers = threadBuffers.get();

        if (buffers._header==null && isHeader(buffer))
            buffers._header=buffer;
        else if (buffers._buffer==null && isBuffer(buffer))
            buffers._buffer=buffer;
        else
            buffers._other=buffer;
    }

    @Override
    public String toString() {
        return "{{"+getHeaderSize()+","+getBufferSize()+"}}";
    }

    /**
     * 每个线程都有三中buffer
     */
    protected static class ThreadBuffers{
        Buffer _buffer;
        Buffer _header;
        Buffer _other;
    }
}
