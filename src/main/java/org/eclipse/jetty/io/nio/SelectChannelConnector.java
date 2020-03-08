package org.eclipse.jetty.io.nio;

import org.eclipse.jetty.util.thread.ThreadPool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * 选择通道的连接器：负责获取连接
 */
public class SelectChannelConnector {
    private ServerSocketChannel acceptChannel; //服务端channel

    private int acceptors = 3; //有_acceptors条线程轮番去调用accept监听请求
    private Thread[] acceptorThreads;

    private SelectorManager selectorManager = new ConnectorSelectorManager();

    private ThreadPool threadPool;

    /**
     * 负责创建服务端channel
     */
    public void open() throws IOException {
        if(acceptChannel == null){
            acceptChannel = ServerSocketChannel.open();
            acceptChannel.configureBlocking(true); //阻塞
            acceptChannel.socket().setReuseAddress(true);
            acceptChannel.socket().bind(new InetSocketAddress(8888));
        }
    }

    /**
     * 启动
     */
    public void doStart() throws Exception{
        open();
        acceptorThreads = new Thread[acceptors];
        for(int i=0; i<acceptorThreads.length; i++){
            Thread t = new Acceptor(i);
            t.start();
        }
    }

    /**
     * 接收请求的入口，多条线程都会阻塞在这里，每次有请求进来都会唤醒一条线程
     */
    public void accept(int acceptorID) throws IOException{
        System.out.println(Thread.currentThread().getName() + " wait request!");
        SocketChannel socketChannel = acceptChannel.accept();
        System.out.println(Thread.currentThread().getName() + " get request!");
        socketChannel.configureBlocking(false);
        selectorManager.register(socketChannel); //将连接放到Selector的队列中去处理
    }

    public ThreadPool getThreadPool(){
        return threadPool;
    }

    /**
     * 内部类，不给外人使用
     */
    private final class ConnectorSelectorManager extends SelectorManager{
        private ConnectorSelectorManager() {}

        @Override
        public boolean dispatch(Runnable task){
            ThreadPool pool = getThreadPool();
            if(pool == null){

            }

            return pool.dispatch(task);
        }
    }

    /**
     * 监听请求的接收器
     */
    private class Acceptor extends Thread{
        int acceptorID = 0;

        public Acceptor(int id){
            this.acceptorID = id;
        }

        @Override
        public void run(){
            Thread current = Thread.currentThread();
            acceptorThreads[acceptorID] = current;
            current.setName("Acceptor-" + acceptorID + "-Thread");

            while (true){
                try {
                    accept(acceptorID);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
