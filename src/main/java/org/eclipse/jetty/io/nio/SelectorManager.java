package org.eclipse.jetty.io.nio;

import org.eclipse.jetty.io.EndPoint;

import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Selector管理器
 */
public abstract class SelectorManager {

    private int _selectSetNum = 3; //Selector的数量
    private AtomicLong _set = new AtomicLong(0L); //处理多少次请求
    private SelectorManager.SelectSet[] _selectSets; //_selectSets个Selector

    public SelectorManager() {
        try {
            doStart();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 注册连接
     */
    public void register(SocketChannel channel) {
        //轮询均分
        long s = _set.decrementAndGet();
        s %= _selectSetNum;

        SelectorManager.SelectSet selectSet = _selectSets[(int)s];
        //入队，并唤醒selector请求处理
        selectSet.addChange(channel);
        selectSet.wakeup();
    }

    //分派任务
    public abstract boolean dispatch(Runnable task);

    public void doStart() throws Exception {
        //创建多个Selector
        _selectSets = new SelectorManager.SelectSet[_selectSetNum];
        for (int i = 0; i < _selectSets.length; i++) {
            _selectSets[i] = new SelectorManager.SelectSet(i);
        }

        for (int i = 0; i < _selectSets.length; i++) {
            Thread t = new Thread(_selectSets[i]);
            t.setName("Selector-" + i);
            t.start();
            System.out.println(t.getName()+" start!");
        }
    }

    /**
     * Selector的包装类，用来处理Acceptor拿到的连接
     */
    public class SelectSet implements Runnable{
        private int _setID;
        //处理队列
        private ConcurrentLinkedQueue<Object> _changes = new ConcurrentLinkedQueue<Object>();
        private Selector _selector;

        public SelectSet(int _setID) throws Exception {
            this._setID = _setID;
            this._selector = Selector.open();
        }

        public void addChange(Object change) {
            _changes.add(change);
        }

        public void wakeup() {
            _selector.wakeup();
        }

        @Override
        public void run(){
            doSelect();
        }

        public void doSelect() {
            Object change = null;
            while (_changes.size() > 0 && (change = _changes.poll()) != null) {
                try {
                    if (change instanceof SocketChannel) { //注册read事件
                        SocketChannel channel = (SocketChannel) change;
                        SelectionKey key = channel.register(_selector, SelectionKey.OP_READ);
                        SelectChannelEndPoint endPoint = newEndPoint(channel, this, key);
                        key.attach(endPoint);
                        endPoint.schedule(); //调度
                    } else if (change instanceof EndPoint) {
                        SelectChannelEndPoint endpointxxxx = (SelectChannelEndPoint)change;
                        change = endpointxxxx.getChannel();
                        endpointxxxx.doUpdateKey();
                    }
                } catch (ClosedChannelException e) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * 创建一个连接通信端点，一个端点需要SocketChannel，选择管理器，注册的SelectionKey
         */
        public SelectChannelEndPoint newEndPoint(SocketChannel channel, SelectSet selectSet, SelectionKey key){
            SelectChannelEndPoint endPoint = new SelectChannelEndPoint(channel, selectSet, key);
            endPoint.setConnection(new AsyncHttpConnection(endPoint));
            return endPoint;
        }

        public Selector get_selector() {
            return _selector;
        }

        public SelectorManager getManager(){
            return SelectorManager.this;
        }
    }
}
