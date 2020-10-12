package fr.umlv.smalljs.jvminterp;

import fr.umlv.smalljs.rt.JSObject;

import java.lang.invoke.*;
import java.lang.invoke.MethodHandles.Lookup;

import static fr.umlv.smalljs.rt.JSObject.UNDEFINED;
import static java.lang.invoke.MethodType.methodType;

public class RT {
    private static final MethodHandle INVOKER, LOOKUP,
            REGISTER, TRUTH, GET_MH, METH_LOOKUP_MH;

    static {
        var lookup = MethodHandles.lookup();
        try {
            INVOKER = lookup.findVirtual(JSObject.class, "invoke", methodType(Object.class, Object.class, Object[].class));
            LOOKUP = lookup.findVirtual(JSObject.class, "lookup", methodType(Object.class, String.class));
            REGISTER = lookup.findVirtual(JSObject.class, "register", methodType(void.class, String.class, Object.class));
            TRUTH = lookup.findStatic(RT.class, "truth", methodType(boolean.class, Object.class));

            GET_MH = lookup.findVirtual(JSObject.class, "getMethodHandle", methodType(MethodHandle.class));
            METH_LOOKUP_MH = lookup.findStatic(RT.class, "lookupMethodHandle", methodType(MethodHandle.class, JSObject.class, String.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    public static Object bsm_undefined(Lookup lookup, String name, Class<?> type) {
        return UNDEFINED;
    }

    public static Object bsm_const(Lookup lookup, String name, Class<?> type, int constant) {
        return constant;
    }

    public static CallSite bsm_funcall(Lookup lookup, String name, MethodType type) {
        var target = INVOKER.asCollector(Object[].class, type.parameterCount() - 2);
        target = target.asType(type);
        return new ConstantCallSite(target);
    }

    /*
    guardwithtest(test, qualifier.methodhandle, getTarget())
     */
    public static CallSite bsm_lookup(Lookup lookup, String name, MethodType type, String functionName) {
        var classLoader = (FunClassLoader) lookup.lookupClass()
                .getClassLoader();
        var globalEnv = classLoader.getGlobal();
        return new ConstantCallSite(MethodHandles.insertArguments(LOOKUP, 0, globalEnv, functionName));
    }


    public static Object bsm_fun(Lookup lookup, String name, Class<?> type, int funId) {
        //throw new UnsupportedOperationException("TODO bsm_fun");
        var classLoader = (FunClassLoader) lookup.lookupClass()
                .getClassLoader();
        var globalEnv = classLoader.getGlobal();
        var fun = classLoader.getDictionary()
                .lookupAndClear(funId);
        return ByteCodeRewriter.createFunction(fun.name()
                .orElse("lambda"), fun.parameters(), fun.body(), globalEnv);
    }

    public static CallSite bsm_register(Lookup lookup, String name, MethodType type, String functionName) {
        //throw new UnsupportedOperationException("TODO bsm_register");
        var classLoader = (FunClassLoader) lookup.lookupClass()
                .getClassLoader();
        var globalEnv = classLoader.getGlobal();
        return new ConstantCallSite(MethodHandles.insertArguments(REGISTER, 0, globalEnv, functionName));
    }

    @SuppressWarnings("unused")  // used by a method handle
    private static boolean truth(Object o) {
        return o != null && o != UNDEFINED && o != Boolean.FALSE;
    }

    public static CallSite bsm_truth(Lookup lookup, String name, MethodType type) {
        return new ConstantCallSite(TRUTH);
    }

    public static CallSite bsm_get(Lookup lookup, String name, MethodType type, String fieldName) {
        throw new UnsupportedOperationException("TODO bsm_get");
        //TODO
    }

    public static CallSite bsm_set(Lookup lookup, String name, MethodType type, String fieldName) {
        throw new UnsupportedOperationException("TODO bsm_set");
        //TODO
    }

    @SuppressWarnings("unused")  // used by a method handle
    private static MethodHandle lookupMethodHandle(JSObject receiver, String fieldName) {
        var function = (JSObject) receiver.lookup(fieldName);
        return function.getMethodHandle();
    }

    public static CallSite bsm_methodcall(Lookup lookup, String name, MethodType type) {
        throw new UnsupportedOperationException("TODO bsm_methodcall");
        //var combiner = insertArguments(METH_LOOKUP_MH, 1, name).asType(methodType(MethodHandle.class, Object.class));
        //var target = foldArguments(invoker(type), combiner);
        //return new ConstantCallSite(target);
    }
}
