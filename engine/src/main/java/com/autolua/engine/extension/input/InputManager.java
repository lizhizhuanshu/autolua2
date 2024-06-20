package com.autolua.engine.extension.input;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.autolua.engine.conceal.InputManagerWrap;

public class InputManager {
    private final InputDevice touchDevice;

    private long lastTouchDown;
    private final PointersState pointersState = new PointersState();
    private final MotionEvent.PointerProperties[] pointerProperties = new MotionEvent.PointerProperties[PointersState.MAX_POINTERS];
    private final MotionEvent.PointerCoords[] pointerCoords = new MotionEvent.PointerCoords[PointersState.MAX_POINTERS];

    private void initPointers() {
        for (int i = 0; i < PointersState.MAX_POINTERS; ++i) {
            MotionEvent.PointerProperties props = new MotionEvent.PointerProperties();
            props.toolType = MotionEvent.TOOL_TYPE_FINGER;
            MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
            coords.orientation = 0;
            coords.size = 1;
            pointerProperties[i] = props;
            pointerCoords[i] = coords;
        }
    }

    private InputManager()
    {
        touchDevice = InputManagerWrap.getTouchDevice();
        initPointers();
        System.out.println("device id = "+touchDevice.getId()
                +"; device source = "+touchDevice.getSources());
    }


    private boolean updateTouch(int action, int pointerIndex, int buttons) {
        long now = SystemClock.uptimeMillis();

        int pointerCount = pointersState.update(pointerProperties, pointerCoords);

        if (pointerCount == 1) {
            if (action == MotionEvent.ACTION_DOWN) {
                lastTouchDown = now;
            }
        } else {
            // secondary pointers must use ACTION_POINTER_* ORed with the pointerIndex
            if (action == MotionEvent.ACTION_UP) {
                action = MotionEvent.ACTION_POINTER_UP | (pointerIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
            } else if (action == MotionEvent.ACTION_DOWN) {
                action = MotionEvent.ACTION_POINTER_DOWN | (pointerIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
            }else{
                System.out.println("action :"+action);
            }
        }
        MotionEvent event = MotionEvent
                .obtain(lastTouchDown, now, action, pointerCount, pointerProperties, pointerCoords, 0, buttons, 1f, 1f,touchDevice.getId(), 0,
                        InputDevice.SOURCE_TOUCHSCREEN, 0);
        return InputManagerWrap.injectInputEvent(event, 0);
    }

    public int touchDown(float x,float y,float major,float minor,float pressure,float size){
        Pointer pointer = pointersState.newPointer();
        if (pointer == null)
            return -1;
        pointer.getPoint().x = x;
        pointer.getPoint().y = y;
        if (major > 0)
            pointer.setMajor(major);
        if (minor > 0)
            pointer.setMinor(minor);
        if (pressure > 0)
            pointer.setPressure(pressure);
        if(size>0)
            pointer.setSize(size);
        int id = pointer.getLocalId();
        int index = pointersState.getPointerIndex(id);
        if (updateTouch(MotionEvent.ACTION_DOWN,index,0)){
            return id;
        }
        return -1;
    }

    public boolean touchMove(int id, float shiftX,float shiftY,float major,float minor,float pressure,float size){
        int index = pointersState.getPointerIndex(id);
        if(index < 0)
            return false;
        Pointer pointer = pointersState.get(index);
        if (shiftX != 0)
            pointer.getPoint().x += shiftX;
        if(shiftY != 0)
            pointer.getPoint().y += shiftY;
        if(major > 0)
            pointer.setMajor(major);
        if(minor > 0)
            pointer.setMinor(minor);
        if (pressure > 0)
            pointer.setPressure(pressure);
        if(size>0)
            pointer.setSize(size);
        boolean result = updateTouch(MotionEvent.ACTION_MOVE,index,0);
        if (!result)
            pointersState.remove(index);
        return result;
    }

    public boolean touchUp(int id){
        int index = pointersState.getPointerIndex(id);
        Pointer pointer = pointersState.get(index);
        pointer.setUp(true);
        return updateTouch(MotionEvent.ACTION_UP,index,0);
    }

    public boolean injectKeyEvent(int action,int keyCode,int repeat,int metaState)
    {
        long now = SystemClock.uptimeMillis();
        KeyEvent event = new KeyEvent(now,now,action,keyCode,repeat,metaState,
                InputManagerWrap.getKeyboardDevice().getId(),0,0,InputDevice.SOURCE_KEYBOARD);
        return InputManagerWrap.injectInputEvent(event,2);
    }

    public void releaseAllPointer()
    {
        for (int i =0;i<pointersState.getSize();i++)
        {
            Pointer pointer = pointersState.get(i);
            if (pointer != null && !pointer.isUp())
            {
                pointer.setUp(true);
                updateTouch(MotionEvent.ACTION_UP,i,0);
            }
        }
    }


    private static final class Default
    {
        private static final InputManager instance = new InputManager();
    }

    public static InputManager getDefault()
    {
        return Default.instance;
    }
}
