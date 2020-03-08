package org.eclipse.jetty.io;

/**
 * Buffers holder的简单实现
 * 1. 分别持有两种缓冲区，获取的时候如果两种容量相同，则可以互相帮助
 * 2. 该实现只能租借两种
 */
public class SimpleBuffers implements Buffers{
    //两种类型各持有一个Buffer
    private final Buffer header;
    private final Buffer buffer;

    //分别对应上面buffer的状态，是否租借出去了，true就是已经租借
    private boolean headerOut;
    private boolean bufferOut;

    //事先就被分配好了
    public SimpleBuffers(Buffer header, Buffer buffer){
        this.header = header;
        this.buffer = buffer;
    }


    @Override
    public Buffer getBuffer(){
        synchronized (this){
            //如果buffer没有被借，则直接返回
            if(buffer!=null && !bufferOut){
                bufferOut = true;
                return buffer;
            }

            //如果buffer，没有就看看容量是否相同，也可以吧header借出去
            if(buffer!=null&&header!=null&&buffer.capacity()==header.capacity()&&!headerOut){
                headerOut = true;
                return header;
            }

            if(buffer!=null){
                return new ByteArrayBuffer(buffer.capacity());
            }
            return new ByteArrayBuffer(4096);
        }
    }

    @Override
    public Buffer getHeader(){
        synchronized (this){
            if(header!=null && !headerOut){
                headerOut = true;
                return header;
            }

            if(buffer!=null&&header!=null&&buffer.capacity()==header.capacity()&&!headerOut){
                bufferOut = true;
                return buffer;
            }

            if(header!=null){
                return new ByteArrayBuffer(header.capacity());
            }
            return new ByteArrayBuffer(4096);
        }
    }

    @Override
    public Buffer getBuffer(int size) {
        synchronized(this) {
            if (header!=null && header.capacity()==size)
                return getHeader();
            if (buffer!=null && buffer.capacity()==size)
                return getBuffer();
            return null;
        }
    }

    @Override
    public void returnBuffer(Buffer buffer) {
        synchronized (this){
            buffer.clear();
            if(buffer == header){
                headerOut = false;
            }
            if(buffer == this.buffer){
                bufferOut = false;
            }
        }
    }
}
