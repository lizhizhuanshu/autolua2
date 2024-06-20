package com.autolua.engine.extension.input;

public interface InputMethod {
    boolean isShown() throws InterruptedException;
    boolean input(String text) throws InterruptedException;
}
