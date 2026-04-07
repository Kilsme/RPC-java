package tech.insight.kilsme.rpc;

import java.util.concurrent.ExecutionException;

public class Main {
    //rpc远程调用 两个不同服务器进行远程函数调用
//    public int add(int a,int b){
//        //consumer ->provider
//        return a+b;
//    }
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        Consumer consumer = new Consumer();

        System.out.println(consumer.add(1,2));
    }
}
