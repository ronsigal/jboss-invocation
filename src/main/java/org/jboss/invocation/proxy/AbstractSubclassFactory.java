/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.invocation.proxy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jboss.classfilewriter.ClassMethod;

/**
 * Class factory for classes that override superclass methods.
 * <p>
 * This class extends {@link AbstractClassFactory} by adding convenience methods to override methods on the superclass.
 *
 * @author Stuart Douglas
 *
 * @param <T> the superclass type
 */
public abstract class AbstractSubclassFactory<T> extends AbstractClassFactory<T> {

    /**
     * Construct a new instance.
     *
     * @param className the class name
     * @param superClass the superclass
     * @param classLoader the defining class loader
     * @param protectionDomain the protection domain
     */
    protected AbstractSubclassFactory(String className, Class<T> superClass, ClassLoader classLoader,
            ProtectionDomain protectionDomain) {
        super(className, superClass, classLoader, protectionDomain);
    }

    /**
     * Construct a new instance.
     *
     * @param className the class name
     * @param superClass the superclass
     * @param classLoader the defining class loader
     */
    protected AbstractSubclassFactory(String className, Class<T> superClass, ClassLoader classLoader) {
        super(className, superClass, classLoader);
    }

    /**
     * Construct a new instance.
     *
     * @param className the class name
     * @param superClass the superclass
     */
    protected AbstractSubclassFactory(String className, Class<T> superClass) {
        super(className, superClass);
    }

    /**
     * Tracks methods that have already been overridden
     */
    private final Set<MethodIdentifier> overriddenMethods = new HashSet<MethodIdentifier>();

    /**
     * Methods that should not be overridden by default
     */
    private static final Set<MethodIdentifier> SKIP_BY_DEFAULT;

    static {
        HashSet<MethodIdentifier> skip = new HashSet<MethodIdentifier>();
        skip.add(MethodIdentifier.EQUALS);
        skip.add(MethodIdentifier.FINALIZE);
        skip.add(MethodIdentifier.HASH_CODE);
        skip.add(MethodIdentifier.TO_STRING);
        SKIP_BY_DEFAULT = Collections.unmodifiableSet(skip);
    }

    /**
     * Creates a new method on the generated class that overrides the given methods, unless a method with the same signature has
     * already been overridden.
     * 
     * @param method The method to override
     * @param identifier The identifier of the method to override
     * @param creator The {@link MethodBodyCreator} used to create the method body
     * @return {@code true} if the method was successfully overridden, {@code false} otherwise
     */
    protected boolean overrideMethod(Method method, MethodIdentifier identifier, MethodBodyCreator creator) {
        if (!overriddenMethods.contains(identifier)) {
            overriddenMethods.add(identifier);
            creator.overrideMethod(classFile.addMethod(method), method);
            return true;
        }
        return false;
    }

    /**
     * Creates a new method on the generated class that overrides the given methods, unless a method with the same signature has
     * already been overridden.
     * 
     * @param method The method to override
     * @param identifier The identifier of the method to override
     * @param creator The {@link MethodBodyCreator} used to create the method body
     * @return {@code false} if the method has already been overriden
     */
    protected boolean overrideMethod(ClassMethod method, MethodIdentifier identifier, MethodBodyCreator creator) {
        if (!overriddenMethods.contains(identifier)) {
            overriddenMethods.add(identifier);
            creator.overrideMethod(method, null);
            return true;
        }
        return false;
    }

    /**
     * Overrides all public methods on the superclass. The default {@link MethodBodyCreator} is used to generate the class body.
     *
     * @param includeEquals {@code true} to include {@link Object#equals(Object)}
     * @param includeHashcode {@code true} to include {@link Object#hashCode()}
     * @param includeToString {@code true} to include {@link Object#toString()}
     */
    protected void overridePublicMethods(boolean includeEquals, boolean includeHashcode, boolean includeToString) {
        overridePublicMethods(getDefaultMethodOverride(), includeEquals, includeHashcode, includeToString);
    }

    /** {@inheritDoc} */
    @Override
    protected void cleanup() {
        overriddenMethods.clear();
    }

    /**
     * Overrides all public methods on the superclass. The given {@link MethodBodyCreator} is used to generate the class body.
     *
     * @param override the method body creator to use
     * @param includeEquals {@code true} to include {@link Object#equals(Object)}
     * @param includeHashcode {@code true} to include {@link Object#hashCode()}
     * @param includeToString {@code true} to include {@link Object#toString()}
     */
    protected void overridePublicMethods(MethodBodyCreator override, boolean includeEquals, boolean includeHashcode,
            boolean includeToString) {
        for (Method method : getSuperClass().getMethods()) {
            MethodIdentifier identifier = MethodIdentifier.getIdentifierForMethod(method);
            if (Modifier.isFinal(method.getModifiers())) {
                continue;
            }
            if (!SKIP_BY_DEFAULT.contains(identifier)) {
                overrideMethod(method, identifier, override);
            }
        }
    }

    /**
     * Calls {@link #overrideAllMethods(MethodBodyCreator)} with the default {@link MethodBodyCreator}.
     */
    protected void overrideAllMethods() {
        overrideAllMethods(getDefaultMethodOverride());
    }

    /**
     * Overrides all methods on the superclass with the exception of <code>equals(Object)</code>, <code>hashCode()</code>,
     * <code>toString()</code> and <code>finalize()</code>. The given {@link MethodBodyCreator} is used to generate the class
     * body.
     * <p>
     * Note that private methods are not actually overridden, and if the sub-class is loaded by a different ClassLoader to the
     * parent class then neither will package-private methods. These methods will still be present on the new class however, and
     * can be accessed via reflection
     *
     * @param override the method body creator to use
     */
    protected void overrideAllMethods(MethodBodyCreator override) {
        Class<?> currentClass = getSuperClass();
        while (currentClass != null) {
            for (Method method : getSuperClass().getDeclaredMethods()) {
                // do not override static or private methods
                if (Modifier.isStatic(method.getModifiers()) || Modifier.isPrivate(method.getModifiers())) {
                    continue;
                }
                MethodIdentifier identifier = MethodIdentifier.getIdentifierForMethod(method);
                // don't attempt to override final methods
                if (Modifier.isFinal(method.getModifiers())) {
                    continue;
                }
                if (!SKIP_BY_DEFAULT.contains(identifier)) {
                    overrideMethod(method, identifier, override);
                }
            }
            currentClass = currentClass.getSuperclass();
        }
    }

    /**
     * Override the equals method using the given {@link MethodBodyCreator}.
     *
     * @param creator the method body creator to use
     */
    protected void overrideEquals(MethodBodyCreator creator) {
        Method equals = null;
        try {
            equals = getSuperClass().getMethod("equals", Object.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        creator.overrideMethod(classFile.addMethod(equals), equals);
    }

    /**
     * Override the hashCode method using the given {@link MethodBodyCreator}.
     *
     * @param creator the method body creator to use
     */
    protected void overrideHashcode(MethodBodyCreator creator) {
        Method hashCode = null;
        try {
            hashCode = getSuperClass().getMethod("hashCode");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        creator.overrideMethod(classFile.addMethod(hashCode), hashCode);
    }

    /**
     * Override the toString method using the given {@link MethodBodyCreator}.
     *
     * @param creator the method body creator to use
     */
    protected void overrideToString(MethodBodyCreator creator) {
        Method toString = null;
        try {
            toString = getSuperClass().getMethod("toString");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        creator.overrideMethod(classFile.addMethod(toString), toString);
    }

    /**
     * Adds an interface to the generated subclass, using the default {@link MethodBodyCreator} to generate the method bodies.
     *
     * @param interfaceClass the interface to add
     */
    protected void addInterface(Class<?> interfaceClass) {
        addInterface(getDefaultMethodOverride(), interfaceClass);
    }

    /**
     * Adds an interface to the generated subclass, using the given {@link MethodBodyCreator} to generate the method bodies
     *
     * @param override the method body creator to use
     * @param interfaceClass the interface to add
     */
    protected void addInterface(MethodBodyCreator override, Class<?> interfaceClass) {
        classFile.addInterface(interfaceClass.getName());
        for (Method method : interfaceClass.getMethods()) {
            override.overrideMethod(classFile.addMethod(method), method);
        }
    }

    /**
     * Adds a constructor for every non-private constructor present on the superclass. The constructor bodies are generated with
     * the default {@link ConstructorBodyCreator}
     */
    protected void createConstructorDelegates() {
        createConstructorDelegates(getDefaultConstructorOverride());
    }

    /**
     * Adds constructors that delegate the the superclass constructor for all non-private constructors present on the superclass
     *
     * @param creator the constructor body creator to use
     */
    protected void createConstructorDelegates(ConstructorBodyCreator creator) {
        for (Constructor<?> constructor : getSuperClass().getDeclaredConstructors()) {
            if (!Modifier.isPrivate(constructor.getModifiers())) {
                creator.overrideConstructor(classFile.addConstructor(constructor), constructor);
            }
        }
    }

    /**
     * Returns the default {@link MethodBodyCreator} to use when creating overridden methods.
     *
     * @return the default method body creator
     */
    public MethodBodyCreator getDefaultMethodOverride() {
        return DefaultMethodBodyCreator.INSTANCE;
    }

    /**
     * Returns the default {@link ConstructorBodyCreator} to use then creating overridden subclasses.
     *
     * @return the default constructor body creator
     */
    public ConstructorBodyCreator getDefaultConstructorOverride() {
        return DefaultConstructorBodyCreator.INSTANCE;
    }
}
