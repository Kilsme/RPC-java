package tech.insight.kilsme.rpc.breaker;

/**
 * 基于“慢调用比例”的滑动窗口熔断器。
 * <p>
 * 核心状态：CLOSED（关闭）、OPEN（打开）、HALF_OPEN（半开）。
 * 在窗口内慢请求占比超过阈值时打开熔断；冷却期后尝试半开探测；
 * 探测成功则关闭，探测失败则再次打开。
 */

import tech.insight.kilsme.rpc.metrices.RpcCallMetrics;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ResponseTimeCircuitBreaker implements CircuitBreaker {
    private final long breakMs = 10000;
    private final long windowDurationMs = 10000;
    //环形数组
    private final long slowRequestMs;//慢请求的标准
    private final long slotMs = 1000;
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final Slot[] slots = new Slot[(int) (windowDurationMs / slotMs)];
    private volatile int currentIndex = 0;
    private volatile long currentTime = System.currentTimeMillis() / slotMs * slotMs;
    private volatile long breakStartTime = 0;//使用reentrantLock保证线程安全并且加上volatile保证可见性
    private final Lock slideLock = new ReentrantLock();
    private final double slowRatio;
    private  final int minRequest = 5;

    public ResponseTimeCircuitBreaker(double slowRatio,long slowRequestMs) {
        for (int i = 0; i < 10; i++) {
            slots[i] = new Slot();
        }
        this.slowRatio=slowRatio;
        this.slowRequestMs=slowRequestMs;
    }
    @Override
    public boolean allowRequest() {
        if (state.get() == State.CLOSED) {
            return true;
        }
        if (state.get() == State.HALF_OPEN) {
            return false;
        }
        if (System.currentTimeMillis() - breakStartTime < breakMs) {
            return false;
        }
        return state.compareAndSet(State.OPEN, State.HALF_OPEN);
    }
    @Override
    public void recordRpc(RpcCallMetrics metrics) {
        long now = System.currentTimeMillis();
        slideWindowIfNecessary(now);
        boolean slowRequest = !metrics.isComplete() || metrics.getDuration() > slowRequestMs;
        switch (state.get()) {
            case OPEN -> processOpen(slowRequest);
            case HALF_OPEN -> processHalfOpen(slowRequest);
            case CLOSED -> processClose(slowRequest);
        }
    }
    private void processOpen(boolean slowRequest) {

    }
    private void processClose(boolean slowRequest) {
        if (!slowRequest) {
            slots[currentIndex].requestCount.incrementAndGet();
            return;
        }
        slots[currentIndex].requestCount.incrementAndGet();
        slots[currentIndex].errorRequestCount.incrementAndGet();
        int totalRequest = 0;
        int totalErrorRequest = 0;
        for (Slot slot : slots) {
            totalErrorRequest += slot.errorRequestCount.get();
            totalRequest += slot.requestCount.get();
        }
        if (totalRequest < minRequest) {
            return;
        }
        double errorRatio = ((double) totalErrorRequest) / totalRequest;
        if (errorRatio > slowRatio && this.state.compareAndSet(State.CLOSED, State.OPEN)) {
            this.breakStartTime = System.currentTimeMillis();

        }
    }

    private void processHalfOpen(boolean slowRequest) {
        if (!slowRequest) {
            //是正常请求
            this.state.compareAndSet(State.HALF_OPEN, State.CLOSED);
            return;
        }
        if (slowRequest) {
            if (this.state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                breakStartTime = System.currentTimeMillis();
            }
        }
    }


    //实现双重锁 保证滑动窗口的线程安全  使用的是环形数组的形式
    private void slideWindowIfNecessary(long now) {
        if (now - currentTime < slotMs) {
            return;
        }
        try {
            //1s 2s如果2s先拿到锁可能会发生线程不安全的问题 但是这个熔断器主要是记录请求状态的所以不需要强烈一致性的
            slideLock.lock();
            int diff = (int) ((now - currentTime) / slotMs);
            if (diff <= 0) {
                return;
            }
            int step = Math.min(slots.length, diff);
            for (int i = 0; i < step; i++) {
                int updateIndex = (currentIndex + i + 1) % slots.length;
                slots[updateIndex].requestCount.set(0);
                slots[updateIndex].errorRequestCount.set(0);
            }
            currentIndex = (currentIndex + diff) % slots.length;
            currentTime = now / slotMs * slotMs;
        } finally {
            slideLock.unlock();
        }
    }

    public class Slot {
        AtomicInteger requestCount = new AtomicInteger(0);
        AtomicInteger errorRequestCount = new AtomicInteger(0);
    }
}
