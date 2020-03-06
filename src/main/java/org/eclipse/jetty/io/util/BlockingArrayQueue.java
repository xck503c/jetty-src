package org.eclipse.jetty.io.util;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
public class BlockingArrayQueue<E> extends AbstractList<E> implements BlockingQueue<E> {

    private final int DEFAULT_CAPACITY = 128;
    private final int DEFAULT_GROWTH = 64;
    private volatile int _capacity; //容量
    private final int _growCapacity; //每次扩容增长容量
    private final int _limit; //容量的限制
    private final AtomicInteger _size = new AtomicInteger(); //队列中的元素数量

    private Object[] _elements; //队列，存储容器

    //两把锁，队头和队尾锁
    private final ReentrantLock _headLock = new ReentrantLock();
    private final ReentrantLock _tailLock = new ReentrantLock();
    private final Condition _notEmpty = _headLock.newCondition();

    private volatile int _head; //队头位置，指向下一个
    private volatile int _tail; //队尾位置，指向下一个

    public BlockingArrayQueue(){
        _elements = new Object[DEFAULT_CAPACITY];
        _growCapacity = DEFAULT_GROWTH;
        _capacity = this._elements.length;
        _limit = Integer.MAX_VALUE; //2147483647=2^31-1
    }

    public BlockingArrayQueue(int capacity, int growBy) {
        _elements = new Object[capacity];
        _capacity = capacity;
        _growCapacity = growBy;
        _limit = Integer.MAX_VALUE;
    }

    public int getCapacity() {
        return this._capacity;
    }

    public int getLimit() {
        return this._limit;
    }

    /**
     * BlockingQueue阻塞队列接口需要实现的操作
     * 1. 入队，从队尾入
     */
    public boolean add(E e) {
        return offer(e);
    }

    public boolean offer(E e) {
        if(e == null){
            throw new NullPointerException();
        }else{
            boolean not_empty = false;
            //1.队尾访问加锁
            _tailLock.lock();
            try {
                //2.是否超过限制
                if(_size.get() >= _limit){
                    return false;
                }
                //3.是否满了，满了就扩容
                if(_size.get() == _capacity){
                    //3-1.对队头进行加锁
                    _headLock.lock();
                    try{
                        if(!grow()){
                            return false;
                        }
                    } finally {
                        //3-2.对队头进行解锁
                        _headLock.unlock();
                    }
                }

                //4.更新信息
                _elements[_tail] = e;
                _tail %= (_tail+1)%_capacity;
                not_empty = 0 == _size.getAndIncrement();
            } finally {
                //5.解锁
                _tailLock.unlock();
            }

            //6.不为空，需要叫醒阻塞在队头的线程
            if(not_empty){
                _headLock.lock();
                try{
                    _notEmpty.signalAll();
                } finally {
                    _headLock.unlock();
                }
            }
            return true;
        }
    }

    //grow的时候处于加锁状态
    private boolean grow(){
        //1.判断扩容增长的量是多少
        if(_growCapacity < 0){
            return false;
        }else{
            //2.依次获得锁，队尾，队头
            _tailLock.lock();
            try {
                _headLock.lock();

                try{
                    int head = _head;
                    int tail = _tail;
                    Object[] elements = new Object[_capacity + _growCapacity];
                    //3.分情况将数据重新整理到开头位置
                    //3-1.若队尾在队头后面，属于正常情况
                    int new_tail = 0;
                    if(head < tail && _size.get()>0){
                        new_tail = tail - head;
                        System.arraycopy(_elements, head, elements, 0, new_tail);
                    }else if(head<=tail && _size.get()==0){ //3-2.若是空的
                        new_tail = 0;
                    }else{
                        //3-3.若队头在队尾的后面，在扩容的时候队头在后面，队尾在前面
                        //先转移后面部分-->到队头
                        int cut = _capacity - _head;
                        System.arraycopy(_elements, head, elements, 0, cut);
                        //转移前面部分到队尾
                        new_tail = cut + _tail;
                        System.arraycopy(_elements, 0, elements, cut, new_tail);
                    }

                    //update
                    _elements = elements;
                    _capacity = _elements.length;
                    _head = 0;
                    _tail = new_tail;
                } finally {
                    _headLock.unlock();
                }
            } finally {
                _tailLock.unlock();
            }
        }
        return true;
    }

    public void put(E e) throws InterruptedException {
        if (!this.add(e)) {
            throw new IllegalStateException("full");
        }
    }

    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    public E take() throws InterruptedException {
        //1.对队头进行加锁，为了保证take可以被中断这里不能使用lock()
        E e = null;
        _headLock.lockInterruptibly();
        try{
            try {
                //2.队列是是空的，空的就阻塞
                while (_size.get() == 0){
                    _notEmpty.await();
                }
            } catch (InterruptedException e1) {
                //等待被中断，唤醒等待线程中的另外一个
                _notEmpty.signal();
                throw e1;
            }

            e = (E)_elements[_head];
            _elements[_head] = null;
            _head = (_head+1) % _capacity;
            if(_size.decrementAndGet() > 0){
                _notEmpty.signalAll();
            }
        }finally {
            _headLock.unlock();
        }
        return e;
    }

    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E e = null;
        //根据单位进行换算，转换为ms
        long nanos = unit.toNanos(timeout);
        _headLock.lockInterruptibly();

        try {
            while (true){
                try {
                    if(_size.get() == 0){
                        if(nanos <=0 ){
                            return e;
                        }

                        nanos = _notEmpty.awaitNanos(nanos); //递减
                        continue;
                    }
                } catch (InterruptedException e1) {
                    _notEmpty.signalAll();
                    throw e1;
                }
                e = (E)this._elements[this._head];
                this._elements[this._head] = null;
                this._head = (this._head + 1) % this._capacity;
                if (this._size.decrementAndGet() > 0) {
                    this._notEmpty.signal();
                }
                break;
            }
        }finally {
            _headLock.unlock();
        }

        return e;
    }

    public int remainingCapacity() {
        this._tailLock.lock();

        int var1;
        try {
            this._headLock.lock();

            try {
                var1 = this.getCapacity() - this.size();
            } finally {
                this._headLock.unlock();
            }
        } finally {
            this._tailLock.unlock();
        }

        return var1;
    }

    public int drainTo(Collection<? super E> c) {
        throw new UnsupportedOperationException();
    }

    public int drainTo(Collection<? super E> c, int maxElements) {
        throw new UnsupportedOperationException();
    }

    @Override
    public E set(int index, E e) {
        if (e == null) {
            throw new NullPointerException();
        } else {
            this._tailLock.lock();

            E var5;
            try {
                this._headLock.lock();

                try {
                    if (index < 0 || index >= this._size.get()) {
                        throw new IndexOutOfBoundsException("!(0<" + index + "<=" + this._size + ")");
                    }

                    int i = this._head + index;
                    if (i >= this._capacity) {
                        i -= this._capacity;
                    }

                    E old = (E)_elements[i];
                    this._elements[i] = e;
                    var5 = old;
                } finally {
                    this._headLock.unlock();
                }
            } finally {
                this._tailLock.unlock();
            }

            return var5;
        }
    }

    @Override
    public void add(int index, E e) {
        if (e == null) {
            throw new NullPointerException();
        } else {
            this._tailLock.lock();

            try {
                this._headLock.lock();

                try {
                    if (index < 0 || index > this._size.get()) {
                        throw new IndexOutOfBoundsException("!(0<" + index + "<=" + this._size + ")");
                    }

                    if (index == this._size.get()) {
                        this.add(e);
                    } else {
                        if (this._tail == this._head && !this.grow()) {
                            throw new IllegalStateException("full");
                        }

                        int i = this._head + index;
                        if (i >= this._capacity) {
                            i -= this._capacity;
                        }

                        this._size.incrementAndGet();
                        this._tail = (this._tail + 1) % this._capacity;
                        if (i < this._tail) {
                            System.arraycopy(this._elements, i, this._elements, i + 1, this._tail - i);
                            this._elements[i] = e;
                        } else {
                            if (this._tail > 0) {
                                System.arraycopy(this._elements, 0, this._elements, 1, this._tail);
                                this._elements[0] = this._elements[this._capacity - 1];
                            }

                            System.arraycopy(this._elements, i, this._elements, i + 1, this._capacity - i - 1);
                            this._elements[i] = e;
                        }
                    }
                } finally {
                    this._headLock.unlock();
                }
            } finally {
                this._tailLock.unlock();
            }

        }
    }

    @Override
    public E remove(int index) {
        E old = null;
        _tailLock.lock();

        try{
            _headLock.lock();

            try{
                if(index<=0 || index>=_size.get()){
                    throw new IndexOutOfBoundsException("!(0<" + index + "<=" + _size.get() + ")");
                }

                int i = _head+index;
                if(i>_capacity){
                    i = i-_capacity;
                }
                old = (E)_elements[i];
                //调整结构
                if(i < _tail){ //正常移除
                    System.arraycopy(_elements, i+1, _elements, i, _tail-i);
                    _tail--;
                    _size.decrementAndGet();
                }
            } finally {
                _headLock.unlock();
            }
        } finally {
            _tailLock.unlock();
        }
        return old;
    }

    @Override
    public void clear() {
        this._tailLock.lock();

        try {
            this._headLock.lock();

            try {
                this._head = 0;
                this._tail = 0;
                this._size.set(0);
            } finally {
                this._headLock.unlock();
            }
        } finally {
            this._tailLock.unlock();
        }
    }

    @Override
    public E get(int index) {
        E e = null;
        //为了保证索引的正确性
        _tailLock.lock();

        try{
            _headLock.lock();

            try{
                if(index<=0 || index>=_size.get()){
                    throw new IndexOutOfBoundsException("!(0<" + index + "<=" + _size.get() + ")");
                }

                int i = _head+index;
                if(i>_capacity){
                    i = i-_capacity;
                }
                e = (E)_elements[i];
            } finally {
                _headLock.unlock();
            }
        } finally {
            _tailLock.unlock();
        }
        return e;
    }

    public E remove() {
        E e = this.poll();
        if (e == null) {
            throw new NoSuchElementException();
        } else {
            return e;
        }
    }

    public E poll() {
        if (this._size.get() == 0) {
            return null;
        } else {
            E e = null;
            this._headLock.lock();

            try {
                if (this._size.get() > 0) {
                    int head = this._head;
                    e = (E)this._elements[head];
                    this._elements[head] = null;
                    this._head = (head + 1) % this._capacity;
                    if (this._size.decrementAndGet() > 0) {
                        this._notEmpty.signal();
                    }
                }
            } finally {
                this._headLock.unlock();
            }

            return e;
        }
    }

    public E element() {
        E e = this.peek();
        if (e == null) {
            throw new NoSuchElementException();
        } else {
            return e;
        }
    }

    public E peek() {
        if (this._size.get() == 0) {
            return null;
        } else {
            E e = null;
            this._headLock.lock();

            try {
                if (this._size.get() > 0) {
                    e = (E)this._elements[this._head];
                }
            } finally {
                this._headLock.unlock();
            }

            return e;
        }
    }

    @Override
    public boolean isEmpty() {
        return this._size.get() == 0;
    }

    @Override
    public int size() {
        return this._size.get();
    }
}
