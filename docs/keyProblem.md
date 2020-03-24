源码性的东西前面整理了很多笔记了，其他有很多的细节，看的有点烦了，现在主要是整理几个关键性的问题这块就算是暂时结了；

#请求的分派

采用一种reactor的模式的变种。我们知道reactor本质是一个事件驱动的模式，什么意思，就是没有事件的时候，我是阻塞等待的状态，有事件的试试，我用notify唤醒，这样就会唤醒。

我们知道服务端会调用accept方法，这个方法可以阻塞多个线程，可以利用这点加快连接的处理。当有请求进来之后，就会唤醒其中一个线程，这个线程就会将其交给SelectorSet，并不是一对一处理，而是有多个SelectorSet，通过轮询的方式进行选择，放到它的change队列。

而每个SelectorSet都有一个doSelect方法，它会去change队列中处理，这个chang队列的类型可以有很多，可以是socketchannel，可以是endpoint。如果有新的请求进来，我们会对其attache一个endppoint，然后调用schedule进行数据的读取，读取之后就会调用handle方法，这个handle方法就是我们自定义的方法，我们可以在里面进行数据处理和响应。

调度schedule这个方法里面会对事件进行一个判断，因为水平触发的关系，也就是只要满足条件就会触发，如果是写事件会反复的触发，这样会很令人头疼。所以它里面就判断些事件然后直接清除，设置一个可写标识。

#连接的管理

请求分派已经写好了，那这个SocketChannel是如何管理的，会怎么保存。我们知道在在SelectionKey上面attach，endpoint对象的时候，就已经将连接的处理绑定了。所以只要有事件触发，就会通过Selector中拿到，并且重新调度。

    SocketChannel channelx = (SocketChannel)change;
    key = channelx.register(selector, 1, (Object)null);
    SelectChannelEndPoint endpointxx = this.createEndPoint(channelx, key);
    key.attach(endpointxx);
    endpointxx.schedule();


好，那如果保存的我们知道了，那如果进行空闲的计时。

注意到：有个endpoint的map，会在创建的时候放入，会在销毁的时候移除


    private ConcurrentMap<SelectChannelEndPoint, Object> _endPoints = new ConcurrentHashMap();


下面这个可以看到对上面这个进行了一个遍历。定时的去检查连接是否超时了


    private static final int __IDLE_TICK = Integer.getInteger("org.eclipse.jetty.io.nio.IDLE_TICK", 400);
    now = System.currentTimeMillis();
    if (now - this._idleTick > (long)SelectorManager.__IDLE_TICK) {
        this._idleTick = now;
        final long idle_now = SelectorManager.this._lowResourcesConnections > 0L && (long)selector.keys().size() > SelectorManager.this._lowResourcesConnections ? now + (long)SelectorManager.this._maxIdleTime - (long)SelectorManager.this._lowResourcesMaxIdleTime : now;
        SelectorManager.this.dispatch(new Runnable() {
            public void run() {
                Iterator i$ = SelectSet.this._endPoints.keySet().iterator();
    
                while(i$.hasNext()) {
                    SelectChannelEndPoint endp = (SelectChannelEndPoint)i$.next();
                    endp.checkIdleTimestamp(idle_now);
                }
    
            }
    
            public String toString() {
                return "Idle-" + super.toString();
            }
        });
    }


首先会有一个默认的检查间隔，也就是400ms，它会根据这个间隔去定时检查连接，检查的方式，是异步的，因为它用了一个回调，真正执行是使用线程池。

我们看到上面有一个时间的计算idle_now：
如果有设置最低的连接数，并且也超过了这个限制：now+maxIdleTime-lowResourceMaxIdleTime
否则就是now
第一种可以翻译成下面两个步骤：
当前时间-上次使用完时间点=空闲时间
空闲时间是否>设置最低资源空闲时间


    public void checkIdleTimestamp(long now) {
        if (this.isCheckForIdle() && this._maxIdleTime > 0) {
            //_idleTimestamp就是上次使用完成后的时间，计算出空闲时间
            final long idleForMs = now - this._idleTimestamp;
            if (idleForMs > (long)this._maxIdleTime) {
                this.setCheckForIdle(false);
                this._manager.dispatch(new Runnable() {
                    public void run() {
                        try {
                            SelectChannelEndPoint.this.onIdleExpired(idleForMs);
                        } finally {
                            SelectChannelEndPoint.this.setCheckForIdle(true);
                        }
    
                    }
                });
            }
        }
    }


如果两个管道都未关闭，那它会先关闭output管道。否则就会直接关闭连接。
临时之前还要看看状态，如果需要dispatch，那还是需要重新dispatch


    synchronized(this) {
        this._onIdle = false;
        if (this._state == -1) {
            this.dispatch();
        }
    }


处理完成之后，就要干掉连接了。

#几个内嵌配置参数的关系

##setAcceptors(int acceptors)
不能超过可用核心数的两倍


    public void setAcceptors(int acceptors) {
        if (acceptors > 2 * Runtime.getRuntime().availableProcessors()) {
            LOG.warn("Acceptors should be <=2*availableProcessors: " + this, new Object[0]);
        }
    
        this._acceptors = acceptors;
    }


这个控制获取连接的线程；

##setLowResourcesConnections
selectSets数量就是accept数量，也就是说监听连接的线程和处理连接的线程一样多。

这个没看懂，反正就是控制数量，

    
    public void setLowResourcesConnections(long lowResourcesConnections) {
        this._lowResourcesConnections = 
            (lowResourcesConnections + (long)this._selectSets - 1L) 
                / (long)this._selectSets;
    }

    public long getLowResourcesConnections() {
        return this._lowResourcesConnections * (long)this._selectSets;
    }


