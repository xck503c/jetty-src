package org.eclipse.jetty.io.nio;

import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

/**
 * channel端点，TCP是端对端通信，以此来比喻
 * 持有对应的socket连接，注册的key，以及Selector
 */
public class SelectChannelEndPoint extends ChannelEndPoint{
    private SocketChannel channel;
    private SelectorManager.SelectSet selectSet;
    private SelectionKey key;

    private static final int STATE_NEEDS_DISPATCH = -1; //需要分派
    private static final int STATE_UNDISPATCHED = 0; //未分派(初始)
    private static final int STATE_DISPATCHED = 1; //已分派
    private static final int STATE_ASYNC = 2;
    private int state;

    private boolean onIdea; //是否空闲

    private int interestOpts; //记录当前感兴趣集合

    //标识，很大程度上代替SelectionKey的作用，因为只要事件如果一直满足条件就会一直触发
    private boolean open; //是否连接打开了
    private boolean writable; //是否可写
    private boolean readBlocked = false; //正在阻塞读
    private boolean writeBlocked = false; //正在阻塞写

    private AsyncHttpConnection conn;

    public SelectChannelEndPoint(SocketChannel channel, SelectorManager.SelectSet selectSet
            , SelectionKey key){
        this.channel = channel;
        this.selectSet = selectSet;
        this.key = key;

        state = STATE_UNDISPATCHED;
        onIdea = false;

        this.writable = true; //默认为可写
        this.open = true; //默认为打开
    }

    public void schedule(){
        synchronized (this){
            //key存在而且有效
            if(this.key!=null && this.key.isValid()){
                //是否出现了阻塞读写
                if(!this.readBlocked && !this.writeBlocked){
                    //判断就绪事件是否为可写，判断感兴趣事件是否为可写，两者皆成立才可以
                    //前面_key.readyOps() & 4==4我这里就直接用封装的方法
                    if(this.key.isWritable()
                            && (this.key.interestOps()&SelectionKey.OP_WRITE)==SelectionKey.OP_WRITE){
                        //我已经知道可以写了，取消该事件防止重复触发，自己设置标识为可写
                        //4的取反就是-5，这样做可以取消该事件的关注
                        this.interestOpts = this.key.interestOps() & (~SelectionKey.OP_WRITE);
                        this.key.interestOps(interestOpts);
                        this.writable = true;
                    }

                    if(state >= STATE_DISPATCHED){ //本次分派已经结束
                        this.key.interestOps(0); //清除所有感兴趣事件
                    } else{ //还需要分派
                        dispatch();
                        if (this.state >= STATE_DISPATCHED) { //成功分派完成要清空
                            key.interestOps(0);
                        }
                    }
                }else { //说明出现了阻塞读或者写
                    //查看是否是阻塞读，是的话，再去看看是否读就绪，如果就绪就重置标识
                    if(readBlocked && key.isReadable()){
                        readBlocked = false;
                    }

                    if(writeBlocked && key.isWritable()){
                        writeBlocked = false;
                    }

                    notifyAll(); //唤醒阻塞读的线程，这个阻塞都有专门的方法
                    key.interestOps(0);
                    //需要分派，但是因为出现了阻塞，所以先唤醒等待，为了可以再次处理，这里更新时间，重新触发
                    if(state < STATE_DISPATCHED){
                        updateKey();
                    }
                }
            }else { //key失效了
                //重置标识
                readBlocked = false;
                writeBlocked = false;
                notifyAll();
            }
        }
    }

    public void dispatch(){
        synchronized(this){
            if(state <= STATE_UNDISPATCHED){
                if(onIdea) {
                    state = STATE_NEEDS_DISPATCH;
                }else {
                    state = STATE_DISPATCHED; //先置为已经分派，防止重复处理

                }
            }
        }
    }

    /**
     * 1. 根据当前情况，来获取需要更新的感兴趣事件是什么，并记录到包装类的缓存中interestOpts
     * 2. 将自身重新放入change队列中处理，等拿到该对象，会自动调用doUpdateKey，这个才是真正更新；
     */
    private void updateKey(){
        boolean change = false;
        synchronized (this){
            int current_ops = -1;
            if(isOpen()){ //更新key的前提是要打开
                boolean read_interest = (!isInputShutdown()) && (readBlocked || state < 1);
                boolean write_interest = (!isOutPutShutdown()) && writeBlocked || state < 1;
                //我们想要更新的感兴趣集合
                interestOpts = (read_interest?SelectionKey.OP_READ:0) |
                        (write_interest?SelectionKey.OP_WRITE:0);
                current_ops = key!=null&&key.isValid()?key.interestOps():-1; //当前感兴趣的集合
            }
            change = interestOpts != current_ops;
        }

        //因为当前感兴趣的集合变化了需要重启处理，这里有一点要注意，这里并没有实际更改SelectionKey中的集合
        if(change){
            selectSet.addChange(this);
            selectSet.wakeup();
        }
    }

    public void doUpdateKey(){
        synchronized (this){
            if(isOpen()){
                if(interestOpts > 0){
                    if(key != null && key.isValid()){
                        key.interestOps(interestOpts);
                    }else {
                        SelectableChannel sc = (SelectableChannel)getChannel();
                        //该channel是否已经注册到Selector上面
                        // key被取消之后，通道也会保持注册一段时间；同理channel也是；会有延迟
                        if(sc.isRegistered()){
                            updateKey(); //多循环几次判断，应该是这个意思
                        }else {
                            //因为本身有事件要更新，而channel又相当于一个全新的状态，就重新注册
                            try {
                                key = sc.register(selectSet.get_selector(), interestOpts, this);
                            } catch (ClosedChannelException e) {

                            }
                        }
                    }
                }else if(key != null && key.isValid()){ //key还是有效的
                    key.interestOps(0);
                }else { //什么都没有，那就没了
                    key = null;
                }
            }else {
                //通道都关闭了，你这个key还维护干嘛，删除了
                if(key != null && key.isValid()){
                    key.cancel();
                }

                if(open){
                    open = false;
                    //从endpoint中移除
                }

                key = null;
            }
        }
    }

    public void handle(){ //处理分派的方法，使用的时候会放到一个Runnable里面进行放入线程池回调执行
        boolean dispatched = true;


    }

    public void setConnection(AsyncHttpConnection conn){
        this.conn = conn;
    }
}
