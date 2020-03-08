package org.eclipse.jetty.io;

/**
 * 一个创建缓冲源的工厂
 */
public class BuffersFactory {

    public static Buffers newBuffers(Buffers.Type headerType, int headerSize
            , Buffers.Type bufferType, int bufferSize, Buffers.Type otherType,int maxSize){
        if (maxSize>=0)
            return new PooledBuffers(headerType,headerSize,bufferType,bufferSize,otherType,maxSize);
        return new ThreadLocalBuffers(headerType,headerSize,bufferType,bufferSize,otherType);
    }
}
