package com.github.aistech.orp.processor;

import java.util.ArrayList;
import java.util.List;

final class Binding {
    private final String name;
    private final String value;
    private final ExtraFieldBinding fieldBinding;

    Binding(String name, String value, ExtraFieldBinding fieldBinding) {
        this.name = name;
        this.value = value;
        this.fieldBinding = fieldBinding;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public ExtraFieldBinding getFieldBinding() {
        return fieldBinding;
    }

    public List<MemberViewBinding> getRequiredBindings() {
        List<MemberViewBinding> requiredBindings = new ArrayList<>();
        if (fieldBinding != null) {
            requiredBindings.add(fieldBinding);
        }
        return requiredBindings;
    }

    public boolean isSingleFieldBinding() {
        return fieldBinding != null;
    }

    public boolean requiresLocal() {
        return !isSingleFieldBinding();
    }

    static final class Builder {
        private final String name;
        private final String value;
        ExtraFieldBinding fieldBinding;

        Builder(String name, String value) {
            this.name = name;
            this.value = value;
        }

        void setFieldBinding(ExtraFieldBinding fieldBinding) {
            if (this.fieldBinding != null) {
                throw new AssertionError();
            }
            this.fieldBinding = fieldBinding;
        }

        Binding build() {
            return new Binding(name, value, fieldBinding);
        }
    }
}
