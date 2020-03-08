package org.eclipse.jetty.io.nio;

import org.eclipse.jetty.io.Connection;

import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

/**
 * https://github.com/eclipse/jetty.project/blob/jetty-8.1.x/jetty-io/src/main/java/org/eclipse/jetty/io/nio/SelectChannelEndPoint.java
 * An Endpoint that can be scheduled by {@link SelectorManager}.
 * 2. channel端点，TCP是端对端通信，以此来比喻
 * 3. 持有对应的socket连接，注册的key，以及Selector，是该连接数据交互的基础
 */
public class SelectChannelEndPoint extends ChannelEndPoint{
    private SocketChannel channel;
    private final SelectorManager.SelectSet selectSet;
    private final SelectorManager manager;
    private SelectionKey key;

    private static final int STATE_NEEDS_DISPATCH = -1; //需要分派
    private static final int STATE_UNDISPATCHED = 0; //未分派(初始)
    private static final int STATE_DISPATCHED = 1; //已分派
    private static final int STATE_ASYNC = 2;
    private int state;

    private boolean onIdea; //是否空闲
    private volatile long ideaTimestamp; //空闲计时初始时间
    private volatile boolean chenkIdle;

    private int interestOpts; //记录当前感兴趣集合

    //标识，很大程度上代替SelectionKey的作用，因为只要事件如果一直满足条件就会一直触发
    private boolean open; //是否连接打开了
    private boolean writable; //是否可写
    private boolean readBlocked = false; //正在阻塞读
    private boolean writeBlocked = false; //正在阻塞写

    private final Runnable handler; //处理器，调用是本类中的handle方法

    private AsyncHttpConnection conn;

    public SelectChannelEndPoint(SocketChannel channel, SelectorManager.SelectSet selectSet
            , SelectionKey key){
        this.channel = channel;
        this.selectSet = selectSet;
        this.manager = selectSet.getManager(); //通过内部类获取外部类实例
        this.key = key;

        state = STATE_UNDISPATCHED;
        onIdea = false; //初始化，默认是不空闲的

        this.writable = true; //默认为可写
        this.open = true; //默认为打开

        handler = new Runnable() {
            @Override
            public void run() {
                SelectChannelEndPoint.this.handle();
            }
        };
    }

    /**
     * 调度：唤醒阻塞读写线程，根据清空进行dispatch到线程池中处理
     * 1. 在这里如果出现了阻塞读写，那就需要唤醒阻塞的线程，唤醒的同时要清空关注事件;
     * 说一下我对这个的看法，因为已经阻塞在读写部分了，说明事件可能没有触发，或者重复触发了。
     * 所以这里需要清空事件。
     * 2. 判断状态，如果是非分派，则需要进行分派，若是在1中清空，则需要重新更新事件，进行触发
     * 3. 针对写事件，有一个取消关注并且设置标识的动作，可以防止重复触发写事件
     */
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
                    //查看是否是阻塞读，是的话，再去看看是否读就绪，如果就绪就重置标识为false
                    if(readBlocked && key.isReadable()){
                        readBlocked = false;
                    }

                    if(writeBlocked && key.isWritable()){
                        writeBlocked = false;
                    }

                    //唤醒阻塞线程，阻塞读写都有专门的方法
                    //唤醒的原因：目前猜测是为了防止无限阻塞
                    notifyAll();
                    //需要清空关注的事件：因为之前已经阻塞读或者写了，说明事件已经被触发过了
                    key.interestOps(0);
                    //如果发现还需要分派，那么就需要重新触发事件，所以
                    //先更新自己的属性interestOpts，然后在将自己放入change队列中等待处理(更新SelectionKey)
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
            if(state <= STATE_UNDISPATCHED){ //需要分派
                if(onIdea) { //处于空闲状态，但是又没有超过阈值
                    state = STATE_NEEDS_DISPATCH;
                }else {
                    state = STATE_DISPATCHED; //先置为已经分派，防止重复处理
                    //交给线程池处理，只要交付成功就会返回true
                    boolean isDispatch = manager.dispatch(handler);
                    if(!isDispatch){
                        //交付失败了
                        state = STATE_NEEDS_DISPATCH;
                        updateKey(); //重新交给SelectSet处理
                    }
                }
            }
        }
    }

    /**
     * 根据情况更新interestOpts，判断若是集合变化了，则重新注册
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

    /**
     * 更新SectionKey关注事件或者注册channel
     * 1. 因为key被取消之后，会出现通道还是打开，通道还是被注册的状态，SectionKey是关联通道和事件集合的记录；
     * 也就是说这里存在延时，所以不知道是不是已经被关闭了，这里循环判断一下；
     * 2. 注意到，这里会在断开链接的时候，移除SelectSet的endpoint队列，也就是销毁
     */
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
                                //出现异常了，你也不要做其他的了，直接取消
                                if(key!=null && key.isValid()){
                                    key.cancel();
                                }

                                if(open){
                                    //从endpoint中移除
                                }

                                open = false;
                                key = null;
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

        while (dispatched){
            //
            while (true){
                AsyncHttpConnection next = (AsyncHttpConnection)conn.handle();
                //response为101的时候需要切换协议进行重新处理
                if(next == conn){
                    break;
                }
                Connection oldConn = conn;
                conn = next;
            }
        }
    }

    /**
     * 是否需要进行连接超时校验
     */
    public void setCheckForIdle(boolean check){
        if(check){
            ideaTimestamp = System.currentTimeMillis();
            chenkIdle = true;
        }else{
            chenkIdle = false;
        }

    }

    public void setConnection(AsyncHttpConnection conn){
        this.conn = conn;
    }
}
