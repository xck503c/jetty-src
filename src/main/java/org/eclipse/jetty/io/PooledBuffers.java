package org.eclipse.jetty.io;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 缓冲池：管理指定数量的缓冲区的租借和回收，当然如果缓存的数量不足也提供了额外的缓冲区创建；
 * 1. 可以看出Buffer的总数是固定的，而一般来说都是优先租借和归还headers和buffers的
 * 缓冲区。
 * 2. others只是做为一个补充，用于猜测不一样的缓冲区大小的队列
 * 这块需要注意指定缓冲区的大小情况，不能太大了，否则会无端占用内存
 */
public class PooledBuffers extends AbstractBuffers{

    private final Queue<Buffer> headers;
    private final Queue<Buffer> buffers;
    private final Queue<Buffer> others;

    private final AtomicInteger size = new AtomicInteger(); //当前可分配缓冲数量
    private final int maxSize;

    private final boolean otherHeaders; //other的类型是否和headerType一样
    private final boolean otherBuffers; //other的类型是否和bufferType一样

    public PooledBuffers(Type headerType, int headerSize
            , Type bufferType, int bufferSize, Type otherType, int maxSize) {
        super(headerType, headerSize, bufferType, bufferSize, otherType);
        this.headers = new ConcurrentLinkedQueue<>();
        this.buffers = new ConcurrentLinkedQueue<>();
        this.others = new ConcurrentLinkedQueue<>();
        this.maxSize = maxSize;
        this.otherHeaders = headerType == otherType;
        this.otherBuffers = headerType == otherType;
    }

    @Override
    public Buffer getHeader() {
        Buffer buffer = headers.poll();
        if(buffer == null){
            buffer = newHeader();
        }else {
            size.decrementAndGet();
        }
        return buffer;
    }

    @Override
    public Buffer getBuffer() {
        Buffer buffer = buffers.poll();
        if (buffer == null)
            buffer = newBuffer();
        else {
            size.decrementAndGet();
        }
        return buffer;
    }

    /**
     * 1. 会先判断类型和容量，优先从header和buffer中取
     * 2. 如果都不行，才会从other中取，可以看到，他会消耗掉other队列中所有的buffer来判断，
     * 清空掉不符合最近一次获取条件的所有缓冲区。
     * @param size
     * @return
     */
    @Override
    public Buffer getBuffer(int size) {
        if(otherHeaders && size == getHeaderSize()){
            return getHeader();
        }else if(otherBuffers && size == getBufferSize()){
            return getBuffer();
        }

        Buffer buffer = others.poll();
        while (buffer!=null && buffer.capacity()!=size){
            this.size.decrementAndGet();
            buffer = others.poll();
        }

        if(buffer == null){
            buffer = newBuffer(size);
        }else {
            this.size.decrementAndGet();
        }
        return buffer;
    }

    @Override
    public void returnBuffer(Buffer buffer) {
        buffer.clear();
        if (buffer.isVolatile() || buffer.isImmutable()) //不可重复使用
            return;

        if(size.incrementAndGet() > maxSize){
            size.decrementAndGet();
        }else {
            if(isHeader(buffer)){
                headers.add(buffer);
            }else if(isBuffer(buffer)){
                buffers.add(buffer);
            }else {
                others.add(buffer); //最后才放入other中
            }
        }
    }

    @Override
    public String toString() {
        return String.format("%s [%d/%d@%d,%d/%d@%d,%d/%d@-]",
                getClass().getSimpleName(),
                headers.size(),maxSize,headerSize,
                buffers.size(),maxSize,bufferSize,
                others.size(),maxSize);
    }
}
