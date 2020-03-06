package org.eclipse.jetty.io.util.component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * aggregate 聚合Bean 有管理很多的东西的感觉
 */
public class AggregateLifeCycle extends AbstractLifeCycle implements Destroyable,Dumpable {
    private final List<Bean> _beans = new CopyOnWriteArrayList<Bean>();
    //该容器是否已经启动，本身也是具有生命周期，也实现了doStart和doStop
    //需要调用的时候直接调用doStart方法
    private boolean _started = false;

    public AggregateLifeCycle(){}

    /**
     * 容器也是具有生命周期，可以调用start方法启动
     * 因为是容器所有启动的时候也要启动容器中未启动的bean
     */
    protected void doStart() throws Exception{
        Iterator it = _beans.iterator();
        while (it.hasNext()){
            Bean b = (Bean)it.next();
            if(b._managed && b._bean instanceof LifeCycle){
                LifeCycle l = (LifeCycle)b._bean;
                if(!l.isRunning()){
                    l.start(); //没启动要启动
                }
            }
        }
        _started = true;
    }

    //stop并不代表不能再次start
    public void doStop() throws Exception{
        _started = false; //关闭容器
        //这里没有看懂为什么要reverse？
        List<AggregateLifeCycle.Bean> reverse = new ArrayList(this._beans);
        Collections.reverse(reverse);
        Iterator i$ = reverse.iterator();

        while(i$.hasNext()) {
            AggregateLifeCycle.Bean b = (AggregateLifeCycle.Bean)i$.next();
            if (b._managed && b._bean instanceof LifeCycle) {
                LifeCycle l = (LifeCycle)b._bean;
                if (l.isRunning()) {
                    l.stop();
                }
            }
        }
    }

    //全部销毁
    public void destroy(){
        List<AggregateLifeCycle.Bean> reverse = new ArrayList(this._beans);
        Collections.reverse(reverse);
        Iterator i$ = reverse.iterator();

        while(i$.hasNext()) {
            AggregateLifeCycle.Bean b = (AggregateLifeCycle.Bean)i$.next();
            if (b._bean instanceof Destroyable && b._managed) {
                Destroyable d = (Destroyable)b._bean;
                d.destroy();
            }
        }
        this._beans.clear();
    }

    /**
     * 管理bean的操作：
     * 1. 添加
     * 2. 移除
     *  (1)移除单个，全部
     * 3. 获取
     *  (1)根据class获取单个，多个
     * 判断操作：
     * 1. 是否包含
     * 2. 是否受到bean管理
     */
    public boolean addBean(Object o){
        return addBean(o, !(o instanceof LifeCycle) || ((LifeCycle)o).isStarted());
    }

    public boolean addBean(Object o, boolean managed){
        if(contains(o)){
            return false;
        }
        Bean b = new Bean(o);
        b._managed = managed;
        _beans.add(b); //不过是否受到管理都会add
        if (o instanceof LifeCycle) { //如果具有生命周期则判断是否需要启动
            LifeCycle l = (LifeCycle)o;
            if (managed && this._started) {
                try {
                    l.start();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return true;
    }

    public boolean contains(Object bean){
        //迭代判断
        Iterator it = _beans.iterator();
        while (it.hasNext()){
            Bean b = (Bean) it.next();
            if(b._bean == bean){ //地址比较
                return true;
            }
        }
        return false;
    }

    public boolean isManaged(Object bean) {
        Iterator i$ = this._beans.iterator();

        while (i$.hasNext()){
            Bean b = (Bean)i$.next();
            if (b._bean == bean){
                return b._managed;
            }
        }

        return false;
    }

    public Collection<Object> getBeans() {
        return this.getBeans(Object.class);
    }

    public <T> List<T> getBeans(Class<T> clazz){
        ArrayList<T> beans = new ArrayList<T>();
        Iterator i$ = this._beans.iterator();

        while (i$.hasNext()){
            Bean b = (Bean)i$.next();
            if (clazz.isInstance(b._bean)){
                beans.add((T)b._bean);
            }
        }

        return beans;
    }

    public <T> T getBean(Class<T> clazz){
        Iterator i$ = this._beans.iterator();

        while (i$.hasNext()){
            Bean b = (Bean)i$.next();
            if (clazz.isInstance(b._bean)){
                return (T)b._bean;
            }
        }

        return null;
    }

    public void removeBeans() {
        this._beans.clear();
    }
    public boolean removeBean(Object o) {
        Iterator i = this._beans.iterator();

        Bean b = null;
        while (i.hasNext()){
            b = (Bean)i.next();
            if(b._bean == o){
                this._beans.remove(b);
                return true;
            }
        }

        return false;
    }

    public String dump(){
        return dump(this);
    }

    public static String dump(Dumpable dumpable) {
        StringBuilder b = new StringBuilder();

        try {
            dumpable.dump(b, "");
        } catch (IOException e) {
            e.printStackTrace();
        }

        return b.toString();
    }

    protected void dumpThis(Appendable out) throws IOException { //toString-state
        out.append(String.valueOf(this)).append(" - ").append(this.getState()).append("\n");
    }

    public static void dumpObject(Appendable out, Object o) throws IOException {
        try {
            if (o instanceof LifeCycle) {
                out.append(String.valueOf(o)).append(" - ").append(getState((LifeCycle)o)).append("\n");
            } else {
                out.append(String.valueOf(o)).append("\n");
            }
        } catch (Throwable var3) {
            out.append(" => ").append(var3.toString()).append('\n');
        }

    }

    public void dump(Appendable out, String indent) throws IOException{
        dumpThis(out); //先导出自己
        if(!_beans.isEmpty()){
            for(int i=0; i<_beans.size(); i++){
                Bean b = _beans.get(i);
                out.append(indent).append(" +- "); //为啥是+-
                if(b._managed&&b._bean instanceof Dumpable){ //调用自己的dump方法
                    ((Dumpable)b._bean).dump(out, indent + (i == _beans.size() ? "    " : " |  "));
                }else {
                    dumpObject(out, b._bean);
                }
            }
        }
    }

    public static void dump(Appendable out, String indent, Collection collection) throws IOException{
        if (collection!=null && !collection.isEmpty()){
            Iterator i$ = collection.iterator();
            int i=0;
            int size = collection.size();
            while (i$.hasNext()){
                Object o = i$.next();
                i++;
                out.append(indent).append(" +- ");
                if (o instanceof Dumpable) {
                    ((Dumpable)o).dump(out, indent + (i == size ? "    " : " |  "));
                } else {
                    dumpObject(out, o);
                }
            }
        }
    }

    /**
     * 代表容器中的元素
     */
    private class Bean{
        final Object _bean; //被封在对象
        /**
         * 是否受到容器管理：
         * 1. 不具有生命周期，默认true
         * 2. 具有生命周期但是还未启动完，需要容器帮你启动，
         *      所以是被容器管理-true
         *  (1)若具有生命周期且已经启动了，而且容器中没有，
         *      说明不是容器启动的，不受到容器管理-false
         * 所以总的来说，这个字段就是是否收到容器所管理；
         *      也可以说是否由容器启动(如果具有生命周期，会实现doStart方法
         *      或者用默认的doStart方法)
         */
        volatile boolean _managed = true;

        Bean(Object b){
            _bean = b;
        }

        @Override
        public String toString(){
            return "{" + this._bean + "," + this._managed + "}";
        }
    }
}
