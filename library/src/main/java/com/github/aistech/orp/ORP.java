package com.github.aistech.orp;

import android.app.Activity;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.util.Log;
import android.view.View;

import com.github.aistech.orp.exceptions.ORPExceptions;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by Bernardo on 29/12/2016.
 */
public class ORP {

    private static final String TAG = "ORP";
    static final Map<Class<?>, Constructor<?>> BINDINGS = new LinkedHashMap<>();

    @NonNull
    @UiThread
    public static void tryToBind(@NonNull Activity target) {
        try {
            bind(target);
        } catch (ORPExceptions e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    @NonNull
    @UiThread
    public static void bind(@NonNull Activity target) {
        View sourceView = target.getWindow().getDecorView();
        createBinding(target, sourceView);
    }

    private static void createBinding(@NonNull Object target, @NonNull View source) {
        Class<?> targetClass = target.getClass();
        Log.d(TAG, "Looking up binding for " + targetClass.getName());
        Constructor<?> constructor = findBindingConstructorForClass(targetClass);

        if (constructor == null) {
            return;
        }

        //noinspection TryWithIdenticalCatches Resolves to API 19+ only type.
        try {
            constructor.newInstance(target, source);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Unable to invoke " + constructor, e);
        } catch (InstantiationException e) {
            throw new RuntimeException("Unable to invoke " + constructor, e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new RuntimeException("Unable to create binding instance.", cause);
        }
    }

    @Nullable
    @CheckResult
    @UiThread
    private static Constructor findBindingConstructorForClass(Class<?> cls) {
        Constructor bindingCtor = BINDINGS.get(cls);
        if (bindingCtor != null) {
            Log.d(TAG, "HIT: Cached in binding map.");
            return bindingCtor;
        }
        String clsName = cls.getName();
        if (clsName.startsWith("android.") || clsName.startsWith("java.")) {
            Log.d(TAG, "MISS: Reached framework class. Abandoning search.");
            return null;
        }
        try {
            Class<?> bindingClass = Class.forName(clsName + "_ORPBinding");
            //noinspection unchecked
            bindingCtor = (Constructor) bindingClass.getConstructor(cls, Object.class);
            Log.d(TAG, "HIT: Loaded binding class and constructor.");
        } catch (ClassNotFoundException e) {
            Log.d(TAG, "Not found. Trying superclass " + cls.getSuperclass().getName());
            bindingCtor = findBindingConstructorForClass(cls.getSuperclass());
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Unable to find binding constructor for " + clsName, e);
        }
        BINDINGS.put(cls, bindingCtor);
        return bindingCtor;
    }
}
