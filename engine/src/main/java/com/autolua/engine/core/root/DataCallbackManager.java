package com.autolua.engine.core.root;

import androidx.collection.LongSparseArray;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DataCallbackManager<T> {
    public interface Callback<T> {
        void onData(int id,T data);
    }
    private static class DataHandler<T> {
        public Callback<T> callback;
        public T data;
    }
    private final LongSparseArray<DataHandler<T>> mDatas = new LongSparseArray<>();
    private final ReentrantReadWriteLock mLock = new ReentrantReadWriteLock();
    private final Lock readLock = mLock.readLock();
    private final Lock writeLock = mLock.writeLock();
    private final AtomicInteger id = new AtomicInteger(0);

    private int newID(){
        while (true) {
            int i = id.incrementAndGet();
            readLock.lock();
            try{
                if (mDatas.get(i) == null) {
                    return i;
                }
            }finally {
                readLock.unlock();
            }
        }
    }

    public int create(Callback<T> callback) {
        int i = newID();
        DataHandler<T> handler = new DataHandler<T>();
        handler.callback = callback;
        writeLock.lock();
        try{
            mDatas.put(i, handler);
        }finally {
            writeLock.unlock();
        }
        return i;
    }

    public int create(){
        return create(null);
    }

    public T waitAndRemote(int id , long timeout) throws InterruptedException {
        DataHandler<T> handler = null;
        readLock.lock();
        try{
            handler = mDatas.get(id);
        }finally {
            readLock.unlock();
        }
        if (handler == null) {
            return null;
        }
        synchronized (handler) {
            handler.wait(timeout);
        }
        writeLock.lock();
        try{
            mDatas.remove(id);
        }finally {
            writeLock.unlock();
        }
        return handler.data;
    }

    public void notify(int id, T data) {
        DataHandler<T> handler = null;
        readLock.lock();
        try{
            handler = mDatas.get(id);
        }finally {
            readLock.unlock();
        }
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
        writeLock.lock();
        try{
            mDatas.clear();
        }finally {
            writeLock.unlock();
        }
    }

    public void remove(int id) {
        writeLock.lock();
        try{
            mDatas.remove(id);
        }finally {
            writeLock.unlock();
        }
    }

}
