package org.sampl.ast.raw

import org.sampl.ast.common.FunctionCategory
import org.sampl.ast.common.FunctionCategory.PRIMITIVE
import org.sampl.ast.common.FunctionCategory.PROVIDED
import org.sampl.ast.common.FunctionCategory.USER_DEFINED
import org.sampl.ast.decorated.DecoratedClassFunction
import org.sampl.ast.decorated.DecoratedExpression
import org.sampl.ast.type.TypeExpr
import org.sampl.environment.TypeCheckingEnv
import org.sampl.exceptions.UnexpectedTypeError

/**
 * [ClassFunction] represents a function declaration of the form:
 * `public/private`([isPublic]) `let` [identifier] ([genericsDeclaration])?
 * [arguments] `:` [returnType] `=` [body],
 * with [identifier] at [identifierLineNo].
 * The function [category] defines its behavior during type checking, interpretation, and code
 * generation.
 *
 * @property category category of the function.
 * @property isPublic whether the function is public.
 * @property identifierLineNo the line number of the identifier for the function.
 * @property identifier the identifier for the function.
 * @property genericsDeclaration the generics declaration.
 * @property arguments a list of arguments passed to the function.
 * @property returnType type of the return value.
 * @property body body part of the function.
 */
internal data class ClassFunction(
        val category: FunctionCategory = USER_DEFINED,
        val isPublic: Boolean, val identifierLineNo: Int, val identifier: String,
        val genericsDeclaration: List<String>,
        val arguments: List<Pair<String, TypeExpr>>,
        val returnType: TypeExpr, val body: Expression
) {

    /**
     * [functionType] reports the functional type of itself.
     */
    val functionType: TypeExpr.Function = TypeExpr.Function(
            argumentTypes = arguments.map { it.second },
            returnType = returnType
    )

    /**
     * [typeCheck] uses the given [environment] to type check this function member and returns
     * an [DecoratedClassFunction] with inferred type.
     *
     * Requires: [environment] must already put all the function members inside to allow mutually
     * recursive functions.
     */
    fun typeCheck(environment: TypeCheckingEnv): DecoratedClassFunction {
        val genericsDeclarationAndArgsAddedEnv = environment.copy(
                declaredTypes = genericsDeclaration.fold(environment.declaredTypes) { acc, s ->
                    acc.put(key = s, value = emptyList())
                },
                normalTypeEnv = arguments.fold(environment.normalTypeEnv) { acc, (n, t) ->
                    acc.put(key = n, value = t)
                }
        )
        functionType.checkTypeValidity(environment = genericsDeclarationAndArgsAddedEnv)
        val bodyExpr: DecoratedExpression = when (category) {
            PRIMITIVE, PROVIDED -> DecoratedExpression.Dummy // Don't check given ones
            USER_DEFINED -> {
                val e = body.typeCheck(environment = genericsDeclarationAndArgsAddedEnv)
                val bodyType = e.type
                UnexpectedTypeError.check(
                        lineNo = identifierLineNo, expectedType = returnType, actualType = bodyType
                )
                e
            }
        }
        return DecoratedClassFunction(
                category = category, isPublic = isPublic, identifier = identifier,
                genericsDeclaration = genericsDeclaration, arguments = arguments,
                returnType = returnType, body = bodyExpr, type = functionType
        )
    }

}
