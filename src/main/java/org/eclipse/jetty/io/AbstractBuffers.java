package org.eclipse.jetty.io;

import org.eclipse.jetty.io.nio.DirectNIOBuffer;
import org.eclipse.jetty.io.nio.IndirectNIOBuffer;

/**
 * 1. 主要定义了两种buffer的类型和大小属性；
 * 2. 提供了创建两种类型缓冲区方法；提供了判断是否是指定类型缓冲区的方法(大小和容量)
 * 3. 提供了创建指定大小的缓冲区的方法，用otherType标识其类型
 */
public abstract class AbstractBuffers implements Buffers{

    protected final Type headerType;
    protected final int headerSize;

    protected final Type bufferType;
    protected final int bufferSize;

    protected final Type otherType;

    public AbstractBuffers(Type headerType, int headerSize, Type bufferType, int bufferSize, Type otherType) {
        this.headerType = headerType;
        this.headerSize = headerSize;
        this.bufferType = bufferType;
        this.bufferSize = bufferSize;
        this.otherType = otherType;
    }

    //获取buffer的字节大小
    public int getBufferSize(){
        return bufferSize;
    }

    public int getHeaderSize(){
        return headerSize;
    }

    //根据类型创建缓冲区
    protected final Buffer newHeader(){
        switch (headerType){
            case BYTE_ARRAY:
                return new ByteArrayBuffer(headerSize);
            case DIRECT:
                return new DirectNIOBuffer(headerSize);
            case INDIRECT:
                return new IndirectNIOBuffer(headerSize);
        }
        throw new IllegalStateException();
    }

    protected final Buffer newBuffer(){
        switch (bufferType){
            case BYTE_ARRAY:
                return new ByteArrayBuffer(bufferSize);
            case DIRECT:
                return new DirectNIOBuffer(bufferSize);
            case INDIRECT:
                return new IndirectNIOBuffer(bufferSize);
        }
        throw new IllegalStateException();
    }

    protected final Buffer newBuffer(int size){
        switch (otherType){
            case BYTE_ARRAY:
                return new ByteArrayBuffer(size);
            case DIRECT:
                return new DirectNIOBuffer(size);
            case INDIRECT:
                return new IndirectNIOBuffer(size);
        }
        throw new IllegalStateException();
    }

    //容量一样，类型一样
    public boolean isHeader(Buffer buffer){
        if (buffer.capacity()==headerSize) {
            switch(headerType) {
                case BYTE_ARRAY:
                    return buffer instanceof ByteArrayBuffer && !(buffer instanceof  IndirectNIOBuffer);
                case DIRECT:
                    return buffer instanceof  DirectNIOBuffer;
                case INDIRECT:
                    return buffer instanceof  IndirectNIOBuffer;
            }
        }
        return false;
    }

    public final boolean isBuffer(Buffer buffer) {
        if (buffer.capacity()==bufferSize) {
            switch(bufferType) {
                case BYTE_ARRAY:
                    return buffer instanceof ByteArrayBuffer && !(buffer instanceof  IndirectNIOBuffer);
                case DIRECT:
                    return buffer instanceof  DirectNIOBuffer;
                case INDIRECT:
                    return buffer instanceof  IndirectNIOBuffer;
            }
        }
        return false;
    }

    public String toString() {
        return String.format("%s [%d,%d]", getClass().getSimpleName(), headerSize, bufferSize);
    }
}
