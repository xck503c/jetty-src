package org.eclipse.jetty.io.xck.test.lockinterupt;

public class LockInteruptMain {
    public static void main(String[] args) throws Exception{
        BussinessClass bc=new BussinessClass();

        Thread t0=new Thread(){
            @Override
            public void run() {
                bc.bFuction();
            }
        };


        Thread t1=new Thread(){
            @Override
            public void run() {
                bc.bFuction();
            }
        };

        String tName=Thread.currentThread().getName();

        System.out.println(tName+"-启动t0！");
        t0.start();
        System.out.println(tName+"-我等个5秒，再启动t1");
        Thread.sleep(5000);
        System.out.println(tName+"-启动t1");
        t1.start();

        System.out.println(tName+"-t1获取不到锁，t0这货睡觉了，没释放，我等个5秒！");
        Thread.sleep(5000);
        System.out.println(tName+"-等了5秒了，不等了，把t1中断了！");
        t1.interrupt();

        t1.join();
        t0.join();
        System.out.println(tName+"-t0，t1结束，我也结束！");
    }
}
