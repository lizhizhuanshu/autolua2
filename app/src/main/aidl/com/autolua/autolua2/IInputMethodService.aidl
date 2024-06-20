// IInputMethodService.aidl
package com.autolua.autolua2;

// Declare any non-default types here with import statements

interface IInputMethodService {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    boolean isShown();
    boolean input(String text);
}