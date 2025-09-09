package com.github.forax.framework.injector;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class InjectorRegistry {
    private final HashMap<Class<?>, Supplier<?>> registry = new HashMap<>();

    public <T> void registerInstance(Class<T> type, T object) {
        Objects.requireNonNull(type, "type is null");
        Objects.requireNonNull(object, "object is null");
        registerProvider(type, () -> object);
    }

    public <T> T lookupInstance(Class<T> type) {
        Objects.requireNonNull(type);
        var supplier = registry.get(type);
        if (supplier == null) {
            throw new IllegalStateException("type not injected " + type.getName());
        }
        return type.cast(supplier.get());
    }

    public <T> void registerProvider(Class<T> type, Supplier<T> supplier) {
        Objects.requireNonNull(type, "type is null");
        Objects.requireNonNull(supplier, "supplier is null");
        var result = registry.putIfAbsent(type, supplier);
        if (result != null) {
            throw new IllegalStateException("already injected " + type.getName());
        }
    }

    static List<PropertyDescriptor> findInjectableProperties(Class<?> type) {
        var beanInfo = Utils.beanInfo(type);
        return Arrays.stream(beanInfo.getPropertyDescriptors())
                .filter(propertyDescriptor -> {
                    var setter = propertyDescriptor.getWriteMethod();
                    return setter != null && setter.isAnnotationPresent(Inject.class);
                })
                .toList();
    }

    static Constructor<?> findConstructor(Class<?> impl) {
        var injectedConstructors = Arrays.stream(impl.getConstructors()).
                filter(constructor -> constructor.isAnnotationPresent(Inject.class)).
                toList();
        return switch(injectedConstructors.size()){
            case 0 -> Utils.defaultConstructor(impl);
            case 1 -> injectedConstructors.getFirst();
            default -> throw new IllegalStateException("multiple injected constructors");
        };
    }

    public <T> void registerProviderClass(Class<T> type, Class<? extends T> impl) {
        Objects.requireNonNull(type, "type is null");
        Objects.requireNonNull(impl, "impl is null");

        var constructor = findConstructor(impl);
        var properties = findInjectableProperties(type);
        registerProvider(type, () -> {
            var arguments = Arrays.stream(constructor.getParameterTypes()).
                    map(this::lookupInstance).
                    toArray();
            var object = Utils.newInstance(constructor, arguments);
            for (var property : properties) {
                var setter = property.getWriteMethod();
                var propertyType = property.getPropertyType();
                var value = lookupInstance(propertyType);
                Utils.invokeMethod(object, setter, value);
            }
            return type.cast(object);
        });
    }

    private void registerProviderClass(Class<?> impl) {
        registerProviderClass2(impl);
    }

    private <T> void registerProviderClass2(Class<T> impl) {
        Objects.requireNonNull(impl, "impl is null");
        registerProviderClass(impl, impl);
    }
}