package org.sampl.eval

import org.sampl.ast.common.FunctionCategory
import org.sampl.ast.decorated.DecoratedExpression
import org.sampl.environment.EvalEnv
import java.util.Arrays

/**
 * [Value] defines a set of supported values during interpretation.
 */
sealed class Value {

    /**
     * [Value] turns the value into an [Any] object.
     */
    abstract val asAny: Any

}

/*
 * ------------------------------------------------------------
 * Part 1: Primitive Values
 * ------------------------------------------------------------
 */

/**
 * [UnitValue] represents the unit value.
 */
object UnitValue : Value() {

    override val asAny: Any get() = Unit

}

/**
 * [IntValue] represents an int value with actual [value].
 */
data class IntValue(val value: Long) : Value() {

    override val asAny: Any get() = value

}

/**
 * [FloatValue] represents a float value with actual [value].
 */
data class FloatValue(val value: Double) : Value() {

    override val asAny: Any get() = value

}

/**
 * [BoolValue] represents a bool value with actual [value].
 */
data class BoolValue(val value: Boolean) : Value() {

    override val asAny: Any get() = value

}

/**
 * [CharValue] represents a char value with actual [value].
 */
data class CharValue(val value: Char) : Value() {

    override val asAny: Any get() = value

}

/**
 * [StringValue] represents a string value with actual [value].
 */
data class StringValue(val value: String) : Value() {

    override val asAny: Any get() = value

}

/**
 * [StringArrayValue] represents a string array value with actual [value].
 */
data class StringArrayValue(val value: Array<String>) : Value() {

    override val asAny: Any get() = Arrays.toString(value)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as StringArrayValue
        return Arrays.equals(value, other.value)
    }

    override fun hashCode(): Int = Arrays.hashCode(value)

}

/*
 * ------------------------------------------------------------
 * Part 2: Class Values
 * ------------------------------------------------------------
 */

/**
 * [VariantValue] represents a variant with [variantIdentifier] and a potential [associatedValue].
 */
data class VariantValue(val variantIdentifier: String, val associatedValue: Value?) : Value() {

    override val asAny: Any get() = this

}

/**
 * [StructValue] represents a struct with a [nameValueMap].
 */
data class StructValue(val nameValueMap: Map<String, Value>) : Value() {

    override val asAny: Any get() = this

}

/*
 * ------------------------------------------------------------
 * Part 3: Function Values
 * ------------------------------------------------------------
 */

/**
 * [ClosureValue] is a function closure of [arguments], function [code] and the [environment].
 * It should also reports the function [category] with an optional [name] (required for
 * non-user-defined functions).
 */
data class ClosureValue(
        val category: FunctionCategory, val name: String? = null, val environment: EvalEnv,
        val arguments: List<String>, val code: DecoratedExpression
) : Value() {

    override val asAny: Any get() = this

}
