package com.hmdp.utils;

public interface ILock {

    /**
     * 尝试获取锁
     *
     * @param timeoutSec 锁持有的超时时间，过期自动释放
     * @return true：获取锁成功，否则反之
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     *
     * @return
     */
    void unlock();

}
