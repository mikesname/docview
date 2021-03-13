package models;

import services.data.ErrorSet;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation to be applied to model fields that indicates that
 * the data member is extracted from an object in the
 * relationships section rather than a data item. This information,
 * while unnecessary for serialisation/deserialisation, is useful
 * when aligning an {@link ErrorSet} instance with the
 * model data to which it refers.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER,ElementType.FIELD,ElementType.METHOD})
public @interface Relation {
	String value();
}
