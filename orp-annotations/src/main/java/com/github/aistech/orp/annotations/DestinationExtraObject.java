package com.github.aistech.orp.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * This annotation should be used if you want to recover the object reference
 * sent by the caller activity.
 * Created by Jonathan Nobre Ferreira on 07/12/16.
 */
@Retention(CLASS)
@Target(FIELD)
public @interface DestinationExtraObject {
    String value() default "";
}