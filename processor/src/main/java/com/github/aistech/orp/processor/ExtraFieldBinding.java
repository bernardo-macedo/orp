package com.github.aistech.orp.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;

final class ExtraFieldBinding implements MemberViewBinding {
  private final String value;
  private final TypeName type;
  private final boolean required;

  ExtraFieldBinding(String value, TypeName type, boolean required) {
    this.value = value;
    this.type = type;
    this.required = required;
  }

  public String getValue() {
    return value;
  }

  public TypeName getType() {
    return type;
  }

  public ClassName getRawType() {
    if (type instanceof ParameterizedTypeName) {
      return ((ParameterizedTypeName) type).rawType; // List<String> -> return List;
    }
    return (ClassName) type;
  }

  @Override public String getDescription() {
    return "field '" + value + "'";
  }

  public boolean isRequired() {
    return required;
  }
}
