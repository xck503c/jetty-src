package org.eclipse.jetty.util.thread;

import org.eclipse.jetty.util.BlockingArrayQueue;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.AggregateLifeCycle;
import org.eclipse.jetty.util.component.Dumpable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 组成
 * 1.线程池_threads
 * 2.工作线程_runnable
 * 3.阻塞队列
 *
 * 线程状态：
 * 1. 空闲：阻塞等待/poll最大存活时间
 * 2. 非空闲：领取任务
 *
 * 线程池的状态
 * 1. 开启 创建工作线程到min
 * 2. 关闭 清空任务队列，停止空闲线程，中断运行的线程，等待设定时间，打印线程栈
 *
 * 线程属性：
 * 1. 存活时间
 * 2. 线程名
 * 3. 是否守护
 * 4. 优先级
 * 5. 启动线程数
 * 6. 空闲线程数
 *
 * 拒绝策略：
 * 1. 抛异常
 *
 * 线程数量控制：
 * 1. min：只要超过最小，就要开始判断存活时间
 * 2. max：超过最大则不创建线程
 *
 * 导出：
 * 1. 线程栈的导出(是否详细打印)
 *
 * 需要学习的点
 * 1. 如何停止线程池
 * 2. 如何dump线程池
 * 3. 一个线程池，如何创建，判断存活时间，领取任务，正常退出，并将计数部分去除
 */
public class QueuedThreadPool extends AbstractLifeCycle implements ThreadPool,Executor, Dumpable {
    //工作线程池
    private final ConcurrentLinkedQueue<Thread> _threads;
    //阻塞队列，因为可能要自定义长度所以不用final
    private BlockingQueue<Runnable> _jobs;
    private Runnable _runnable; //工作线程

    private long _maxIdleTimeMs; //最大空闲时间
    private long _maxStopTime; //停止最大等待时间
    private int _maxThreads; //工作线程上限
    private int _minThreads; //线程池中需要保持的线程数量
    private int _maxQueued; //阻塞队列长度

    private String _name; //线程名
    private int _priority;
    private boolean _daemon;
    private boolean _detailedDump;

    //空闲计数
    private AtomicInteger _threadsIdle = new AtomicInteger(0);
    //启动计数
    private AtomicInteger _threadsStarted = new AtomicInteger(0);

    public QueuedThreadPool(){
        _threads = new ConcurrentLinkedQueue<Thread>();
        _maxIdleTimeMs = 60000;
        _maxStopTime = 100;
        _maxThreads = 254; //不知道为什么254和8
        _minThreads = 8;
        _maxQueued = -1; //若<0则使用_minThreads

        _name = "qtq-"+hashCode();
        _priority = 5;
        _daemon = false;
        this._detailedDump = false;

        _runnable = new Runnable() {
            private long lastTime = -1;
            public void run() {
                boolean shrink = false;
                try {
                    //1.若线程池启动就一直循环
                    while(isRunning()){
                        //2.若队列中有任务就一去领取
                        Runnable job = null;
                        while (isRunning() && (job=_jobs.poll())!=null){
                            runJob(job);
                        }

                        try {
                            //3.暂时没有任务了，处于空闲状态+1
                            _threadsIdle.incrementAndGet();
                            lastTime = System.currentTimeMillis(); //进入空闲状态记录时间

                            while (isRunning()){
                                if(job!=null){
                                    runJob(job);
                                    break;
                                }

                                //4-1.若<0说明工作线程长命百岁，直接take阻塞
                                if(_maxIdleTimeMs<0){
                                    job = _jobs.take();
                                }else{
                                    //4-2.判断是否超过最小线程数；判断是否超过了最大存活时间；开启线程数-1
                                    int size = _threadsStarted.get();
                                    if(size > _minThreads){
                                        if(System.currentTimeMillis()-lastTime > _maxIdleTimeMs){
                                            shrink = _threadsStarted.compareAndSet(size, size-1);
                                            if(shrink){
                                                return;
                                            }
                                        }
                                    }
                                    job = idleJobPoll();
                                }
                            }
                        } finally {
                            //5.有任务了，空闲-1
                            _threadsIdle.decrementAndGet();
                            lastTime = -1;
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    //6.线程池缩减成功，进行后续清理
                    if(shrink){
                        _threadsStarted.decrementAndGet();
                    }
                    _threads.remove(Thread.currentThread());
                }
            }
        };
    }

    public QueuedThreadPool(int maxThreads) {
        this();
        this.setMaxThreads(maxThreads);
    }

    public QueuedThreadPool(BlockingQueue<Runnable> jobQ) {
        this();
        this._jobs = jobQ;
        this._jobs.clear();
    }

    //组件启动
    protected void doStart() throws Exception{
        _threadsStarted.set(0);
        if(_jobs == null){
            //居然还有这种可以增长的队列，厉害厉害
            _jobs = _maxQueued>0?new ArrayBlockingQueue<Runnable>(_maxQueued):new BlockingArrayQueue(_minThreads, _minThreads);
        }

        //启动工作线程
        for(int threadStart=_threadsStarted.get(); threadStart<_minThreads; threadStart=_threadsStarted.get()){
            startThread(threadStart);
        }
    }

    protected void doStop() throws Exception{
        long start = System.currentTimeMillis();
        while(this._threadsStarted.get() > 0 && System.currentTimeMillis() - start < (long)(this._maxStopTime / 2)) {
            Thread.sleep(1L);
        }

        //1.先清除任务队列
        _jobs.clear();

        //2.空闲线程数量>0，只有一种可能阻塞了take等待
        int size = _threadsIdle.get();
        if(size>0){
            //通过下面方法解决take阻塞
            Runnable noop = new Runnable() {
                public void run() {
                }
            };
            while(size-- > 0) {
                this._jobs.offer(noop);
            }
            Thread.yield(); //让步给他们执行，让他们退出
        }
        //3. 若启动线程数量>0，中断，让他们跳出等待
        if (this._threadsStarted.get() > 0) {
            Iterator i$ = this._threads.iterator();

            while(i$.hasNext()) {
                Thread thread = (Thread)i$.next();
                thread.interrupt();
            }
        }

        //4.等待线程处理，并让步执行
        while(this._threadsStarted.get() > 0 && System.currentTimeMillis() - start < _maxStopTime) {
            Thread.sleep(1L);
        }
        Thread.yield();

        //5.如果还有线程存在(线程退出后会被移除)，则打印调用栈(我们平时调试通过抓取线程栈也是同理)
        size = _threads.size();
        if (size > 0) {
            System.out.println(size + " threads could not be stopped");
            boolean isDebug = false; //暂时为false
            if (size == 1 || isDebug) {
                Iterator i$ = this._threads.iterator();

                while(i$.hasNext()) {
                    Thread unstopped = (Thread)i$.next();
                    System.out.println("Couldn't stop " + unstopped);
                    StackTraceElement[] arr$ = unstopped.getStackTrace(); //调用栈
                    int len$ = arr$.length;

                    for(int i = 0; i < len$; i++) {
                        StackTraceElement element = arr$[i];
                        System.out.println(" at " + element);
                    }
                }
            }
        }
    }

    private boolean startThread(int threads) {
        int next = threads+1;
        if(!_threadsStarted.compareAndSet(threads, next)){
            return false;
        }
        boolean started = false;

        try {
            Thread thread = new Thread(_runnable);
            thread.setDaemon(this._daemon);
            thread.setPriority(this._priority);
            thread.setName(this._name + "-" + thread.getId());
            _threads.add(thread);
            thread.start();
            started = true;
        } finally {
            if(!started){ //启动失败要还原
                _threadsStarted.decrementAndGet();
            }
        }

        return started;
    }

    //开始解决领取的任务
    protected void runJob(Runnable job){
        job.run();
    }

    //提供两个调用入口，一个是自定义dispatch，一个是适配jdk的execute
    public boolean dispatch(Runnable job){
        if(isRunning() && _jobs.offer(job)){
            int idle = _threadsIdle.get();
            int jobQ = _jobs.size();
            if(idle==0 || jobQ>idle){
                int threads = _threadsStarted.get();
                if(threads<_maxThreads){
                    startThread(jobQ);
                }
            }
            return true;
        }

        //可以打印一个debug

        return false;
    }
    public void execute(Runnable job){
        if(!dispatch(job)){
            throw new RejectedExecutionException();
        }
    }

    //setter and getter
    public void setName(String name){
        if(isRunning()){ //为了统一
            throw new IllegalStateException("started");
        }else {
            _name = name;
        }
    }
    public void setThreadsPriority(int priority){
        _priority = priority;
    }
    public void setDaemon(boolean daemon) {
        this._daemon = daemon;
    }
    public void setMaxQueued(int max) {
        if (this.isRunning()) {
            throw new IllegalStateException("started");
        } else {
            this._maxQueued = max;
        }
    }
    public void setMaxStopTimeMs(int stopTimeMs) {
        this._maxStopTime = stopTimeMs;
    }
    public void setMaxThreads(int maxThreads) {
        this._maxThreads = maxThreads;
        if (this._minThreads > this._maxThreads) {
            this._minThreads = this._maxThreads;
        }

    }

    public void setMinThreads(int minThreads) {
        this._minThreads = minThreads;
        if (this._minThreads > this._maxThreads) {
            this._maxThreads = this._minThreads;
        }

        for(int threads = this._threadsStarted.get(); this.isStarted() && threads < this._minThreads; threads = this._threadsStarted.get()) {
            this.startThread(threads);
        }

    }
    public void setDetailedDump(boolean detailedDump) {
        this._detailedDump = detailedDump;
    }
    public int getMaxQueued() {
        return this._maxQueued;
    }
    public long getMaxIdleTimeMs() {
        return this._maxIdleTimeMs;
    }

    public long getMaxStopTimeMs() {
        return this._maxStopTime;
    }
    public int getMaxThreads() {
        return this._maxThreads;
    }
    public int getMinThreads() {
        return this._minThreads;
    }
    public String getName() {
        return this._name;
    }
    public int getThreadsPriority() {
        return this._priority;
    }
    public boolean isDaemon() {
        return this._daemon;
    }
    public boolean isDetailedDump() {
        return this._detailedDump;
    }

    public int getThreads() {
        return this._threadsStarted.get();
    }

    public int getIdleThreads() {
        return this._threadsIdle.get();
    }

    public boolean isLowOnThreads() {
        return this._threadsStarted.get() == this._maxThreads && this._jobs.size() >= this._threadsIdle.get();
    }

    public String toString() {
        return this._name + "{" + this.getMinThreads() + "<=" + this.getIdleThreads() + "<=" + this.getThreads() + "/" + this.getMaxThreads() + "," + (this._jobs == null ? -1 : this._jobs.size()) + "}";
    }
    private Runnable idleJobPoll() throws InterruptedException {
        return this._jobs.poll(this._maxIdleTimeMs, TimeUnit.MILLISECONDS);
    }


    //dump
    public String dump() {
        return AggregateLifeCycle.dump(this);
    }
    public void dump(Appendable out, String indent) throws IOException {
        List<Object> dump = new ArrayList<Object>(getMaxThreads());
        //遍历线程池
        Iterator i$ = _threads.iterator();
        while (i$.hasNext()){
            final Thread thread = (Thread) i$.next(); //不懂为什么要用final，怕引用没了？
            StackTraceElement[] trace = thread.getStackTrace(); //线程栈
            boolean inIdleJobPoll = false;
            if (trace != null) {
                for(int i=0; i<trace.length; i++){
                    StackTraceElement t = trace[i];
                    if ("idleJobPoll".equals(t.getMethodName())) { //如果是处于idleJobPoll方法则不需要打印线程栈
                        inIdleJobPoll = true;
                        break;
                    }
                }
            }
            //是否需要详细打印
            if(_detailedDump){
                final boolean isPrintStack = inIdleJobPoll;
                dump.add(new Dumpable() { //利用多态，后面统一调用
                    public void dump(Appendable out, String indent) throws IOException {
                        out.append(String.valueOf(thread.getId())).append(' ').append(thread.getName()).append(' ').append(thread.getState().toString()).append(isPrintStack ? " IDLE" : "").append('\n');
                            if (!isPrintStack) {
                            AggregateLifeCycle.dump(out, indent, Arrays.asList(trace)); //打印线程栈
                        }
                    }

                    public String dump() {
                        return null;
                    }
                });
            }else {
                dump.add(thread.getId() + " " + thread.getName() + " " + thread.getState() + " @ " + (trace.length > 0 ? trace[0] : "???") + (inIdleJobPoll ? " IDLE" : ""));
            }
        }
        AggregateLifeCycle.dumpObject(out, this);
        AggregateLifeCycle.dump(out, indent, dump);
    }
}