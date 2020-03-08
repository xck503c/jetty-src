package org.eclipse.jetty.io.nio;

import org.eclipse.jetty.io.Buffer;
import org.eclipse.jetty.io.EndPoint;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;

/**
 * 操作socket的基础类，提供了默认实现的方法；
 * 1. 关闭输入输出流，关闭连接，从channel中读写数据；
 * 2. 获取地址，一些判断，设置阻塞时间等
 */
public class ChannelEndPoint implements EndPoint {
    protected final ByteChannel channel; //一个扩展接口，其下层有SocketChannel，FileChannel等
    protected final Socket socket; //如果是SocketChannel，那就肯定有一个socket连接

    protected final ByteBuffer[] _gather2 = new ByteBuffer[2]; //为了可以批量写入

    //解析地址的对象
    protected final InetSocketAddress local;
    protected final InetSocketAddress remote;

    private volatile int maxIdleTime;
    private volatile boolean isInputShutdown;
    private volatile boolean isOutputShutdown;

    public ChannelEndPoint(ByteChannel channel) throws IOException{
        this.channel = channel;
        this.socket = channel instanceof SocketChannel ? ((SocketChannel) channel).socket() : null;
        if(socket != null){
            this.local = (InetSocketAddress)socket.getLocalSocketAddress();
            this.remote = (InetSocketAddress)socket.getRemoteSocketAddress();
            this.maxIdleTime = socket.getSoTimeout(); //超时间时间默认是阻塞读时间
        }else {
            local = remote = null;
        }
    }

    protected ChannelEndPoint(ByteChannel channel, int maxIdleTime) throws IOException {
        this.channel = channel;
        this.maxIdleTime = maxIdleTime;
        this.socket = channel instanceof SocketChannel ? ((SocketChannel)channel).socket() : null;
        if (this.socket != null) {
            this.local = (InetSocketAddress)this.socket.getLocalSocketAddress();
            this.remote = (InetSocketAddress)this.socket.getRemoteSocketAddress();
            this.socket.setSoTimeout(this.maxIdleTime); //可以自己指定，但是可以看到是protected也就是不对外使用
        } else {
            this.local = this.remote = null;
        }
    }

    @Override
    public void shutdownOutput() throws IOException {
        this.isOutputShutdown = true; //先置标识为true
        if(channel.isOpen() && socket!=null){ //判断是否关闭了
            try {
                if(!socket.isOutputShutdown()){
                    socket.shutdownOutput(); //最终关闭输出流还是调用底层socket
                }
            } catch (SocketException e){
                e.printStackTrace();
            } finally {
                if(isInputShutdown()){ //输入输出流都关闭了，那就关闭该连接
                    close();
                }
            }
        }
    }

    /**
     * 判断是否关闭的方法有多种
     * @return
     */
    @Override
    public boolean isOutPutShutdown() {
        return isOutputShutdown || !channel.isOpen() || socket!=null && socket.isOutputShutdown();
    }

    @Override
    public void shutdownInput() throws IOException {
        this.isInputShutdown = true; //先置标识为true
        if(channel.isOpen() && socket!=null){ //判断是否关闭了
            try {
                if(!socket.isInputShutdown()){
                    socket.shutdownInput(); //最终关闭输出流还是调用底层socket
                }
            } catch (SocketException e){
                e.printStackTrace();
            } finally {
                if(isOutPutShutdown()){ //输入输出流都关闭了，那就关闭该连接
                    close();
                }
            }
        }
    }

    @Override
    public boolean isInputShutdown() {
        return isInputShutdown || !channel.isOpen() || socket!=null && socket.isInputShutdown();
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    @Override
    public int fill(Buffer buffer) throws IOException {
        if(isInputShutdown()){ //已经被关闭了
            return -1;
        }

        Buffer buf = buffer.buffer();
        if(buf instanceof NIOBuffer){
            NIOBuffer nioBuffer = (NIOBuffer)buf;
            ByteBuffer byteBuffer = nioBuffer.getByteBuffer();

            int len = -1;
            try {
                //真正和socket交互读取数据还是用jdk提供的HeapByteBuffer；
                //而之后，还是利用jetty包装的类ByteBuffer进行操作
                synchronized (byteBuffer){
                    try {
                        byteBuffer.position(nioBuffer.putIndex());
                        len = channel.read(byteBuffer); //读取字节数组到缓冲区中
                    } finally {
                        buffer.setPutIndex(byteBuffer.position()); //当前放入的位置
                        byteBuffer.position(0); //设置为0？是清理吗
                    }
                }

                //读取字节-1说明到头了，那就先关闭输入流
                if (len < 0 && this.isOpen()) {
                    if (!isInputShutdown()) {
                        shutdownInput();
                    }

                    if (isOutPutShutdown()) {
                        this.channel.close();
                    }
                }
            } catch (IOException e) {
                try {
                    if (this.channel.isOpen()) {
                        this.channel.close();
                    }
                } catch (IOException e1) {
                }

                if(len>0) throw e; //向上抛出

                len = -1;
            }
            return len;
        }else {
            throw new IOException("Not Implemented");
        }
    }

    @Override
    public int flush(Buffer buffer) throws IOException {
        Buffer buf = buffer.buffer();
        int len = 0;
        try {
            if(buf instanceof NIOBuffer){
                NIOBuffer nioBuffer = (NIOBuffer) buf;
                ByteBuffer byteBuffer = nioBuffer.getByteBuffer().asReadOnlyBuffer(); //只读
                //先更新position和limit，对应get和put指针
                byteBuffer.position(nioBuffer.getIndex());
                byteBuffer.limit(nioBuffer.putIndex());

                len = channel.write(nioBuffer.getByteBuffer());
            }else if(buf instanceof RandomAccessFileBuffer){
                RandomAccessFileBuffer rasBuffer = (RandomAccessFileBuffer) buf;
                rasBuffer.writeTo(channel, buffer.getIndex(), buffer.length());
            } else {
                if (buffer.array() == null) { //这里如果硬要使用peek感觉也是可以
                    throw new IOException("Not Implemented");
                }
                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer.array(), buffer.getIndex(), buffer.length());
                len = channel.write(byteBuffer);
            }
        } finally {
            if(len > 0){
                buffer.skip(len); //最后更新包装类
            }
        }
        return len;
    }

    @Override
    public int flush(Buffer header, Buffer buffer, Buffer trailer) throws IOException {
        int length = 0;
        Buffer buf0 = header == null ? null : header.buffer();
        Buffer buf1 = buffer == null ? null : buffer.buffer();
        //GatheringByteChannel提供了可以批量写入的接口方法
        //但是我不懂为什么trailer会没有
        if (this.channel instanceof GatheringByteChannel && header != null && header.length() != 0 && buf0 instanceof NIOBuffer && buffer != null && buffer.length() != 0 && buf1 instanceof NIOBuffer) {
            length = this.gatheringFlush(header, ((NIOBuffer)buf0).getByteBuffer(), buffer, ((NIOBuffer)buf1).getByteBuffer());
        } else {
            if (header != null && header.length() > 0) {
                length = this.flush(header);
            }

            if ((header == null || header.length() == 0) && buffer != null && buffer.length() > 0) {
                length += this.flush(buffer);
            }

            if ((header == null || header.length() == 0) && (buffer == null || buffer.length() == 0) && trailer != null && trailer.length() > 0) {
                length += this.flush(trailer);
            }
        }

        return length;
    }

    protected int gatheringFlush(Buffer header, ByteBuffer bbuf0, Buffer buffer, ByteBuffer bbuf1) throws IOException {
        synchronized(this) {
            //获取两部分的拷贝
            bbuf0 = bbuf0.asReadOnlyBuffer();
            bbuf0.position(header.getIndex());
            bbuf0.limit(header.putIndex());
            bbuf1 = bbuf1.asReadOnlyBuffer();
            bbuf1.position(buffer.getIndex());
            bbuf1.limit(buffer.putIndex());
            //收集
            this._gather2[0] = bbuf0;
            this._gather2[1] = bbuf1;
            int length = (int)((GatheringByteChannel)this.channel).write(this._gather2);
            //判断写入长度，看看是不是要都跳过
            int hl = header.length();
            if (length > hl) {
                header.clear();
                buffer.skip(length - hl);
            } else if (length > 0) {
                header.skip(length);
            }

            return length;
        }
    }

    public ByteChannel getChannel() {
        return this.channel;
    }

    @Override
    public String getLocalAddr() {
       if(socket == null){
           return null;
       }else {
           if(local!=null && local.getAddress()!=null && local.getAddress().isAnyLocalAddress()){
               return local.getAddress().getHostAddress();
           }else {
               return "127.0.0.1";
           }
       }
    }

    public String getLocalHost() {
        if (this.socket == null) {
            return null;
        } else {
            return this.local != null && this.local.getAddress() != null && !this.local.getAddress().isAnyLocalAddress() ? this.local.getAddress().getCanonicalHostName() : "0.0.0.0";
        }
    }

    public int getLocalPort() {
        if (this.socket == null) {
            return 0;
        } else {
            return this.local == null ? -1 : this.local.getPort();
        }
    }

    public String getRemoteAddr() {
        if (this.socket == null) {
            return null;
        } else {
            return this.remote == null ? null : this.remote.getAddress().getHostAddress();
        }
    }

    public String getRemoteHost() {
        if (this.socket == null) {
            return null;
        } else {
            return this.remote == null ? null : this.remote.getAddress().getCanonicalHostName();
        }
    }

    public int getRemotePort() {
        if (this.socket == null) {
            return 0;
        } else {
            return this.remote == null ? -1 : this.remote.getPort();
        }
    }

    @Override
    public boolean isBlocking() {
        //A channel that can be multiplexed via a {@link Selector}.
        //估计只有实现了这个接口才能判断是否阻塞，否则默认都是阻塞
        if(channel instanceof SelectableChannel){
            return ((SelectableChannel)channel).isBlocking();
        }else {
            return true;
        }
    }

    @Override
    public boolean blockReadable(long millisecs) throws IOException {
        return true;
    }

    @Override
    public boolean blockWritable(long millisecs) throws IOException {
        return true;
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public Object getTransport() {
        return channel;
    }

    @Override
    public void flush() throws IOException {

    }

    @Override
    public int getMaxIdleTime() {
        return maxIdleTime;
    }

    @Override
    public void setMaxIdleTime(int timeMs) throws IOException {
        //如果阻塞时间不对要重新设置
        if(socket!=null && timeMs!=maxIdleTime){
            socket.setSoTimeout(timeMs);
        }
        maxIdleTime = timeMs;
    }
}
