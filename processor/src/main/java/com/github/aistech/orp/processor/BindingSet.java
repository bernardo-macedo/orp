package com.github.aistech.orp.processor;

import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

import static com.google.auto.common.MoreElements.getPackage;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * A set of all the bindings requested by a single type.
 */
final class BindingSet {
    private static final ClassName OBJECT = ClassName.get("java.lang", "Object");
    private static final ClassName CONTEXT = ClassName.get("android.content", "Context");
    private static final ClassName UI_THREAD = ClassName.get("android.support.annotation", "UiThread");
    private static final ClassName CALL_SUPER = ClassName.get("android.support.annotation", "CallSuper");
    private static final ClassName UNBINDER = ClassName.get("com.github.aistech.orp", "Unbinder");
    private static final ClassName SINGLETON = ClassName.get("com.github.aistech.orp.singletons", "ORPSingleton");

    private final TypeName targetTypeName;
    private final ClassName bindingClassName;
    private final boolean isFinal;
    private final ImmutableList<Binding> bindings;
    private final BindingSet parentBinding;
    private final ClassName parentClassName;

    private BindingSet(TypeName targetTypeName, ClassName bindingClassName, boolean isFinal,
                       ImmutableList<Binding> bindings, ClassName parentClassName, BindingSet parentBinding) {
        this.isFinal = isFinal;
        this.targetTypeName = targetTypeName;
        this.bindingClassName = bindingClassName;
        this.bindings = bindings;
        this.parentBinding = parentBinding;
        this.parentClassName = parentClassName;
    }

    JavaFile brewJava(int sdk) {
        return JavaFile.builder(bindingClassName.packageName(), createType(sdk))
                .addFileComment("Generated code from ORP Compiler. Do not modify!")
                .build();
    }

    private TypeSpec createType(int sdk) {
        TypeSpec.Builder result = TypeSpec.classBuilder(bindingClassName.simpleName())
                .addModifiers(PUBLIC);
        if (isFinal) {
            result.addModifiers(FINAL);
        }

        if (parentBinding != null) {
            result.superclass(parentBinding.bindingClassName);
        } else if (parentClassName != null) {
            result.superclass(parentClassName);
            result.addSuperinterface(UNBINDER);
        } else {
            result.addSuperinterface(UNBINDER);
        }

        if (hasFieldBindings()) {
            result.addField(targetTypeName, "target", PRIVATE);
        }

        if (!constructorNeedsView()) {
            // Add a delegating constructor with a target type + view signature for reflective use.
            result.addMethod(createBindingViewDelegateConstructor(targetTypeName));
        }
        result.addMethod(createBindingConstructor(targetTypeName, sdk));

        if (hasExtraFieldBindings() || parentBinding == null) {
            result.addMethod(createBindingUnbindMethod(result, targetTypeName));
        }

        return result.build();
    }

    private MethodSpec createBindingViewDelegateConstructor(TypeName targetType) {
        return MethodSpec.constructorBuilder()
                .addJavadoc("@deprecated Use {@link #$T($T, $T)} for direct creation.\n    "
                                + "Only present for runtime invocation through {@code ButterKnife.bind()}.\n",
                        bindingClassName, targetType, CONTEXT)
                .addAnnotation(Deprecated.class)
                .addAnnotation(UI_THREAD)
                .addModifiers(PUBLIC)
                .addParameter(targetType, "target")
                .addParameter(OBJECT, "source")
                .addStatement(("this(target, source.getContext())"))
                .build();
    }

    private MethodSpec createBindingConstructor(TypeName targetType, int sdk) {
        MethodSpec.Builder constructor = MethodSpec.constructorBuilder()
                .addAnnotation(UI_THREAD)
                .addModifiers(PUBLIC);

        constructor.addParameter(targetType, "target");

        if (constructorNeedsView()) {
            constructor.addParameter(OBJECT, "source");
        } else {
            constructor.addParameter(CONTEXT, "context");
        }

        if (parentBinding != null) {
            if (parentBinding.constructorNeedsView()) {
                constructor.addStatement("super(target, source)");
            } else if (constructorNeedsView()) {
                constructor.addStatement("super(target, source.getContext())");
            } else {
                constructor.addStatement("super(target, context)");
            }
            constructor.addCode("\n");
        }

        if (hasExtraFieldBindings()) {
            constructor.addStatement("this.target = target");
            constructor.addCode("\n");

            constructor.addStatement("$T singleton = $T.getInstance()", SINGLETON, SINGLETON);

            if (hasViewLocal()) {
                // Local variable in which all views will be temporarily stored.
                constructor.addStatement("$T object", OBJECT);
            }
            for (Binding binding : bindings) {
                addViewBinding(constructor, binding);
            }

        }

        return constructor.build();
    }

    private MethodSpec createBindingUnbindMethod(TypeSpec.Builder bindingClass,
                                                 TypeName targetType) {
        MethodSpec.Builder result = MethodSpec.methodBuilder("unbind")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC);
        if (!isFinal && parentBinding == null) {
            result.addAnnotation(CALL_SUPER);
        }

        if (hasFieldBindings()) {
            result.addStatement("$T target = this.target", targetType);
            result.addStatement("if (target == null) throw new $T($S)", IllegalStateException.class,
                    "Bindings already cleared.");
            result.addStatement("$N = null", hasFieldBindings() ? "this.target" : "target");
            result.addCode("\n");
            for (Binding binding : bindings) {
                if (binding.getFieldBinding() != null) {
                    result.addStatement("target.$L = null", binding.getFieldBinding().getValue());
                }
            }
        }

        if (parentBinding != null) {
            result.addCode("\n");
            result.addStatement("super.unbind()");
        }
        return result.build();
    }


    private void addViewBinding(MethodSpec.Builder result, Binding binding) {
//        if (binding.isSingleFieldBinding()) {
//            // Optimize the common case where there's a single binding directly to a field.
//            ExtraFieldBinding fieldBinding = binding.getFieldBinding();
//            CodeBlock.Builder builder = CodeBlock.builder()
//                    .add("target.$L = ", fieldBinding.getValue());
//
//            boolean requiresCast = requiresCast(fieldBinding.getType());
//            if (!requiresCast && !fieldBinding.isRequired()) {
//                builder.add("source.findViewById($L)", binding.getValue());
//            } else {
//                builder.add("$T.find", UTILS);
//                builder.add(fieldBinding.isRequired() ? "RequiredView" : "OptionalView");
//                if (requiresCast) {
//                    builder.add("AsType");
//                }
//                builder.add("(source, $L", binding.getValue());
//                if (fieldBinding.isRequired() || requiresCast) {
//                    builder.add(", $S", asHumanDescription(singletonList(fieldBinding)));
//                }
//                if (requiresCast) {
//                    builder.add(", $T.class", fieldBinding.getRawType());
//                }
//                builder.add(")");
//            }
//            result.addStatement("$L", builder.build());
//            return;
//        }

        List<MemberViewBinding> requiredBindings = binding.getRequiredBindings();
        if (requiredBindings.isEmpty()) {
            result.addStatement("object = ($L) singleton.getParametersForOriginActivity(target.hashCode(), $S)",
                    binding.getFieldBinding().getRawType().toString(), binding.getName());
        }

        addFieldBinding(result, binding);
    }

    private void addFieldBinding(MethodSpec.Builder result, Binding binding) {
        ExtraFieldBinding fieldBinding = binding.getFieldBinding();
        if (fieldBinding != null) {
            result.addStatement("$T.out.println(target.getActivityCallerHashCode())", System.class);
            result.addStatement("target.$L = ($L) singleton.getParametersForOriginActivity(target.getActivityCallerHashCode(), $S)", fieldBinding.getValue(), fieldBinding.getRawType().toString(), fieldBinding.getValue());
        }
    }

    static String asHumanDescription(Collection<? extends MemberViewBinding> bindings) {
        Iterator<? extends MemberViewBinding> iterator = bindings.iterator();
        switch (bindings.size()) {
            case 1:
                return iterator.next().getDescription();
            case 2:
                return iterator.next().getDescription() + " and " + iterator.next().getDescription();
            default:
                StringBuilder builder = new StringBuilder();
                for (int i = 0, count = bindings.size(); i < count; i++) {
                    if (i != 0) {
                        builder.append(", ");
                    }
                    if (i == count - 1) {
                        builder.append("and ");
                    }
                    builder.append(iterator.next().getDescription());
                }
                return builder.toString();
        }
    }


    /**
     * True when this type's bindings require a view hierarchy.
     */
    private boolean hasExtraFieldBindings() {
        return !bindings.isEmpty();
    }


    private boolean hasFieldBindings() {
        for (Binding bindings : this.bindings) {
            if (bindings.getFieldBinding() != null) {
                return true;
            }
        }
        return false;
    }

    private boolean hasViewLocal() {
        for (Binding bindings : this.bindings) {
            if (bindings.requiresLocal()) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if this binding requires a view. Otherwise only a context is needed.
     */
    private boolean constructorNeedsView() {
        return hasExtraFieldBindings() //
                || parentBinding != null && parentBinding.constructorNeedsView();
    }

    @Override
    public String toString() {
        return bindingClassName.toString();
    }

    static Builder newBuilder(TypeElement enclosingElement) {
        TypeName targetType = TypeName.get(enclosingElement.asType());
        if (targetType instanceof ParameterizedTypeName) {
            targetType = ((ParameterizedTypeName) targetType).rawType;
        }

        String packageName = getPackage(enclosingElement).getQualifiedName().toString();
        String className = enclosingElement.getQualifiedName().toString().substring(
                packageName.length() + 1).replace('.', '$');
        ClassName parentClassName = ClassName.get(packageName, className);
        ClassName bindingClassName = ClassName.get(packageName, className + "_ORPBinding");

        boolean isFinal = enclosingElement.getModifiers().contains(Modifier.FINAL);
        return new Builder(targetType, bindingClassName, parentClassName, isFinal);
    }

    static final class Builder {
        private final TypeName targetTypeName;
        private final ClassName bindingClassName;
        private final ClassName parentClassName;
        private final boolean isFinal;

        private BindingSet parentBinding;

        private final Map<String, Binding.Builder> viewIdMap = new LinkedHashMap<>();

        private Builder(TypeName targetTypeName, ClassName bindingClassName, ClassName parentClassName, boolean isFinal) {
            this.targetTypeName = targetTypeName;
            this.bindingClassName = bindingClassName;
            this.parentClassName = parentClassName;
            this.isFinal = isFinal;
        }

        void addField(String name, String value, ExtraFieldBinding binding) {
            getOrCreateViewBindings(name, value).setFieldBinding(binding);
        }

        void setParent(BindingSet parent) {
            this.parentBinding = parent;
        }

        private Binding.Builder getOrCreateViewBindings(String name, String value) {
            Binding.Builder viewId = viewIdMap.get(name);
            if (viewId == null) {
                viewId = new Binding.Builder(name, value);
                viewIdMap.put(name, viewId);
            }
            return viewId;
        }

        BindingSet build() {
            ImmutableList.Builder<Binding> viewBindings = ImmutableList.builder();
            for (Binding.Builder builder : viewIdMap.values()) {
                viewBindings.add(builder.build());
            }
            return new BindingSet(targetTypeName, bindingClassName, isFinal, viewBindings.build(), parentClassName, parentBinding);
        }
    }
}
