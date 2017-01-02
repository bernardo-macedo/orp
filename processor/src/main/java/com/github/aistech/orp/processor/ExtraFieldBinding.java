package com.github.aistech.orp.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;

final class ExtraFieldBinding implements MemberViewBinding {
  private final String value;
  private final TypeName type;

  ExtraFieldBinding(String value, TypeName type) {
    this.value = value;
    this.type = type;
  }

  String getValue() {
    return value;
  }

  ClassName getRawType() {
    if (type instanceof ParameterizedTypeName) {
      return ((ParameterizedTypeName) type).rawType; // List<String> -> return List;
    }
    return (ClassName) type;
  }

  @Override public String getDescription() {
    return "field '" + value + "'";
  }

}
