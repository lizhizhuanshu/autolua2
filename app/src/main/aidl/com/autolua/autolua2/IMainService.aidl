// IMainService.aidl
package com.autolua.autolua2;
import  com.autolua.autolua2.IInputMethodService;
// Declare any non-default types here with import statements

interface IMainService {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
     void bind(IInputMethodService input);
     void unbind();
}