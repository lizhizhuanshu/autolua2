package com.autolua.autolua2.base;

import androidx.collection.LongSparseArray;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DataCallbackManager {
    public interface Callback {
        void onData(int id,byte[] data);
    }
    private static class DataHandler {
        public Callback callback;
        public byte[] data;
    }
    private final LongSparseArray<DataHandler> mDatas = new LongSparseArray<>();
    private final ReentrantReadWriteLock mLock = new ReentrantReadWriteLock();

    private final AtomicInteger id = new AtomicInteger(0);

    private int newID(){
        while (true) {
            int i = id.incrementAndGet();
            mLock.readLock().lock();
            if (mDatas.get(i) == null) {
                mLock.readLock().unlock();
                return i;
            }
            mLock.readLock().unlock();
        }
    }

    public int create(Callback callback) {
        int i = newID();
        DataHandler handler = new DataHandler();
        handler.callback = callback;
        mLock.writeLock().lock();
        mDatas.put(i, handler);
        mLock.writeLock().unlock();
        return i;
    }

    public int create(){
        return create(null);
    }

    public byte[] waitAndRemote(int id , long timeout) throws InterruptedException {
        DataHandler handler = null;
        mLock.readLock().lock();
        handler = mDatas.get(id);
        mLock.readLock().unlock();
        if (handler == null) {
            return null;
        }
        synchronized (handler) {
            handler.wait(timeout);
        }
        mLock.writeLock().lock();
        mDatas.remove(id);
        mLock.writeLock().unlock();
        return handler.data;
    }

    public void notify(int id, byte[] data) {
        DataHandler handler = null;
        mLock.readLock().lock();
        handler = mDatas.get(id);
        mLock.readLock().unlock();
        if (handler == null) {
            return;
        }
        if(handler.callback != null) {
            handler.callback.onData(id,data);
            return;
        }
        synchronized (handler) {
            handler.data = data;
            handler.notify();
        }
    }

    public void clear() {
        mLock.writeLock().lock();
        mDatas.clear();
        mLock.writeLock().unlock();
    }

    public void remove(int id) {
        mLock.writeLock().lock();
        mDatas.remove(id);
        mLock.writeLock().unlock();
    }

}
