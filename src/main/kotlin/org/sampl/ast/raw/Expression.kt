package org.sampl.ast.raw

import org.sampl.ast.common.BinaryOperator
import org.sampl.ast.common.BinaryOperator.AND
import org.sampl.ast.common.BinaryOperator.DIV
import org.sampl.ast.common.BinaryOperator.F_DIV
import org.sampl.ast.common.BinaryOperator.F_MINUS
import org.sampl.ast.common.BinaryOperator.F_MUL
import org.sampl.ast.common.BinaryOperator.F_PLUS
import org.sampl.ast.common.BinaryOperator.GE
import org.sampl.ast.common.BinaryOperator.GT
import org.sampl.ast.common.BinaryOperator.LAND
import org.sampl.ast.common.BinaryOperator.LE
import org.sampl.ast.common.BinaryOperator.LOR
import org.sampl.ast.common.BinaryOperator.LT
import org.sampl.ast.common.BinaryOperator.MINUS
import org.sampl.ast.common.BinaryOperator.MOD
import org.sampl.ast.common.BinaryOperator.MUL
import org.sampl.ast.common.BinaryOperator.OR
import org.sampl.ast.common.BinaryOperator.PLUS
import org.sampl.ast.common.BinaryOperator.SHL
import org.sampl.ast.common.BinaryOperator.SHR
import org.sampl.ast.common.BinaryOperator.STRUCT_EQ
import org.sampl.ast.common.BinaryOperator.STRUCT_NE
import org.sampl.ast.common.BinaryOperator.STR_CONCAT
import org.sampl.ast.common.BinaryOperator.USHR
import org.sampl.ast.common.BinaryOperator.XOR
import org.sampl.ast.common.Literal
import org.sampl.ast.decorated.DecoratedExpression
import org.sampl.ast.decorated.DecoratedPattern
import org.sampl.ast.type.TypeDeclaration
import org.sampl.ast.type.TypeExpr
import org.sampl.ast.type.boolTypeExpr
import org.sampl.ast.type.charTypeExpr
import org.sampl.ast.type.floatTypeExpr
import org.sampl.ast.type.intTypeExpr
import org.sampl.ast.type.stringTypeExpr
import org.sampl.ast.type.unitTypeExpr
import org.sampl.environment.TypeCheckingEnv
import org.sampl.exceptions.GenericsError
import org.sampl.exceptions.IdentifierError
import org.sampl.exceptions.PatternMatchingError
import org.sampl.exceptions.StructError
import org.sampl.exceptions.TooManyArgumentsError
import org.sampl.exceptions.UnexpectedTypeError
import org.sampl.exceptions.VariantNotFoundError
import org.sampl.util.inferActualGenericTypeInfo
import java.util.LinkedList

/**
 * [Expression] represents a set of supported expression.
 */
sealed class Expression {

    /**
     * [typeCheck] returns the decorated expression with the inferred type  under the given
     * [environment].
     *
     * If the type checking failed, it should throw [UnexpectedTypeError] to indicate what's wrong.
     */
    abstract fun typeCheck(environment: TypeCheckingEnv): DecoratedExpression

}

/**
 * [LiteralExpr] represents a [literal] as an expression.
 */
data class LiteralExpr(val literal: Literal) : Expression() {

    override fun typeCheck(environment: TypeCheckingEnv): DecoratedExpression =
            DecoratedExpression.Literal(literal = literal, type = literal.inferredType)

}

/**
 * [VariableIdentifierExpr] represents a [variable] identifier as an expression.
 * It can only contain [genericInfo] which helps to determine the fixed type for this expression.
 */
data class VariableIdentifierExpr(
        val variable: String, private val genericInfo: List<TypeExpr>
) : Expression() {

    override fun typeCheck(environment: TypeCheckingEnv): DecoratedExpression {
        environment.normalTypeEnv[variable]?.let { typeExpr ->
            return DecoratedExpression.VariableIdentifier(
                    variable = variable, genericInfo = emptyList(), type = typeExpr
            )
        }
        // Not found in normal environment, try function environment
        val functionTypeInfo = environment.classFunctionTypeEnv[variable]
                ?: throw IdentifierError.UndefinedIdentifier(badIdentifier = variable)
        val genericSymbolsToSubstitute = functionTypeInfo.genericsInfo
        if (genericSymbolsToSubstitute.size != genericInfo.size) {
            throw GenericsError.GenericsInfoWrongNumberOfArguments(
                    expectedNumber = genericSymbolsToSubstitute.size,
                    actualNumber = genericInfo.size
            )
        }
        val substitutionMap = genericSymbolsToSubstitute.zip(genericInfo).toMap()
        val type = functionTypeInfo.typeExpr.substituteGenerics(map = substitutionMap)
        return DecoratedExpression.VariableIdentifier(
                variable = variable, genericInfo = genericInfo, type = type
        )
    }
}

/**
 * [ConstructorExpr] represents a set of constructor expression defined in type declarations.
 */
sealed class ConstructorExpr : Expression() {

    /**
     * [constructorTypeCheck] with environment [e] is a more constrained type check that is only
     * allowed to produce [DecoratedExpression.Constructor].
     */
    protected abstract fun constructorTypeCheck(e: TypeCheckingEnv): DecoratedExpression.Constructor

    final override fun typeCheck(environment: TypeCheckingEnv): DecoratedExpression =
            constructorTypeCheck(e = environment)

    /**
     * [NoArgVariant] represents a singleton value in variant with [typeName], [variantName] and
     * some potential [genericInfo] to assist type inference.
     */
    data class NoArgVariant(
            val typeName: String, val variantName: String,
            val genericInfo: List<TypeExpr>
    ) : ConstructorExpr() {

        override fun constructorTypeCheck(e: TypeCheckingEnv): DecoratedExpression.Constructor {
            val (genericDeclarations, typeDeclarations) = e.typeDefinitions[typeName]
                    ?: throw IdentifierError.UndefinedTypeIdentifier(badIdentifier = typeName)
            val variantDeclarationMap = (typeDeclarations as? TypeDeclaration.Variant)?.map
                    ?: throw VariantNotFoundError(typeName = typeName, variantName = variantName)
            if (!variantDeclarationMap.containsKey(key = variantName)) {
                throw VariantNotFoundError(typeName = typeName, variantName = variantName)
            }
            if (genericDeclarations.size != genericInfo.size) {
                throw GenericsError.GenericsInfoWrongNumberOfArguments(
                        expectedNumber = genericDeclarations.size, actualNumber = genericInfo.size
                )
            }
            val type = TypeExpr.Identifier(type = typeName, genericsInfo = genericInfo)
            return DecoratedExpression.Constructor.NoArgVariant(
                    typeName = typeName, variantName = variantName, genericInfo = genericInfo,
                    type = type
            )
        }

    }

    /**
     * [OneArgVariant] represents a tagged enum in variant with [typeName], [variantName] and
     * associated [data].
     */
    data class OneArgVariant(
            val typeName: String, val variantName: String, val data: Expression
    ) : ConstructorExpr() {

        override fun constructorTypeCheck(e: TypeCheckingEnv): DecoratedExpression.Constructor {
            val (genericDeclarations, typeDeclarations) = e.typeDefinitions[typeName]
                    ?: throw IdentifierError.UndefinedTypeIdentifier(badIdentifier = typeName)
            val variantDeclarationMap = (typeDeclarations as? TypeDeclaration.Variant)?.map
                    ?: throw VariantNotFoundError(typeName = typeName, variantName = variantName)
            val declaredVariantType = variantDeclarationMap[variantName]
                    ?: throw VariantNotFoundError(typeName = typeName, variantName = variantName)
            val decoratedData = data.typeCheck(environment = e)
            val decoratedDataType = decoratedData.type
            val inferredGenericInfo = inferActualGenericTypeInfo(
                    genericDeclarations = genericDeclarations,
                    genericType = declaredVariantType, actualType = decoratedDataType
            )
            val type = TypeExpr.Identifier(type = typeName, genericsInfo = inferredGenericInfo)
            return DecoratedExpression.Constructor.OneArgVariant(
                    typeName = typeName, variantName = variantName, data = decoratedData,
                    type = type
            )
        }

    }

    /**
     * [Struct] represents a struct initialization with [typeName] and initial value [declarations].
     */
    data class Struct(
            val typeName: String, val declarations: Map<String, Expression>
    ) : ConstructorExpr() {

        override fun constructorTypeCheck(e: TypeCheckingEnv): DecoratedExpression.Constructor {
            val (genericDeclarations, typeDeclarations) = e.typeDefinitions[typeName]
                    ?: throw IdentifierError.UndefinedTypeIdentifier(badIdentifier = typeName)
            val structDeclarationMap = (typeDeclarations as? TypeDeclaration.Struct)?.map
                    ?: throw StructError.NotFound(structName = typeName)
            val decoratedDeclarations = hashMapOf<String, DecoratedExpression>()
            val inferredGenericInfo = structDeclarationMap.map { (declaredName, declaredType) ->
                val expr = declarations[declaredName] ?: throw StructError.MissingMember(
                        structName = typeName, missingMember = declaredName
                )
                val decoratedExpr = expr.typeCheck(environment = e)
                decoratedDeclarations[declaredName] = decoratedExpr
                val exprType = decoratedExpr.type
                declaredType to exprType
            }.let { pairs ->
                inferActualGenericTypeInfo(
                        genericDeclarations = genericDeclarations,
                        genericTypeActualTypePairs = pairs
                )
            }
            val type = TypeExpr.Identifier(type = typeName, genericsInfo = inferredGenericInfo)
            return DecoratedExpression.Constructor.Struct(
                    typeName = typeName, declarations = decoratedDeclarations, type = type
            )
        }

    }

    /**
     * [StructWithCopy] represents a copy of [old] struct with some new values in [newDeclarations].
     */
    data class StructWithCopy(
            val old: Expression, val newDeclarations: Map<String, Expression>
    ) : ConstructorExpr() {

        override fun constructorTypeCheck(e: TypeCheckingEnv): DecoratedExpression.Constructor {
            val decoratedOld = old.typeCheck(environment = e)
            val expectedFinalType = decoratedOld.type as? TypeExpr.Identifier
                    ?: throw UnexpectedTypeError(expectedType = "<struct>",
                            actualType = decoratedOld.type)
            val expectedActualGenericInfo = expectedFinalType.genericsInfo
            val structName = expectedFinalType.type
            val (genericDeclarations, typeDeclarations) = e.typeDefinitions[structName]
                    ?: throw IdentifierError.UndefinedTypeIdentifier(badIdentifier = structName)
            val structDeclarationMap = (typeDeclarations as? TypeDeclaration.Struct)
                    ?.map
                    ?: throw StructError.NotFound(structName = structName)
            val replacementMap = genericDeclarations.zip(expectedActualGenericInfo).toMap()
            val actualTypeMap = structDeclarationMap.mapValues { (_, typeWithGenerics) ->
                typeWithGenerics.substituteGenerics(map = replacementMap)
            }
            val decoratedNewDeclarations = newDeclarations.mapValues { (newName, newExpr) ->
                val expectedType = actualTypeMap[newName]
                        ?: throw StructError.NoSuchMember(
                                structName = structName, memberName = newName)
                val decoratedExpr = newExpr.typeCheck(environment = e)
                val exprType = decoratedExpr.type
                if (expectedType != exprType) {
                    throw UnexpectedTypeError(expectedType = expectedType, actualType = exprType)
                }
                decoratedExpr
            }
            return DecoratedExpression.Constructor.StructWithCopy(
                    old = decoratedOld, newDeclarations = decoratedNewDeclarations,
                    type = expectedFinalType
            )
        }

    }

}

/**
 * [StructMemberAccessExpr] represents accessing [memberName] of [structExpr].
 */
data class StructMemberAccessExpr(
        val structExpr: Expression, val memberName: String
) : Expression() {

    override fun typeCheck(environment: TypeCheckingEnv): DecoratedExpression {
        val decoratedStructExpr = structExpr.typeCheck(environment = environment)
        val structType = decoratedStructExpr.type as? TypeExpr.Identifier
                ?: throw UnexpectedTypeError(expectedType = "<struct>",
                        actualType = decoratedStructExpr.type)
        val structTypeName = structType.type
        val (genericDeclarations, typeDeclarations) = environment.typeDefinitions[structTypeName]
                ?: throw IdentifierError.UndefinedTypeIdentifier(badIdentifier = structTypeName)
        val structDeclarationMap = (typeDeclarations as? TypeDeclaration.Struct)?.map
                ?: throw StructError.NotFound(structName = structTypeName)
        val memberTypeDeclaration = structDeclarationMap[memberName]
                ?: throw StructError.MissingMember(structTypeName, memberName)
        val replacementMap = genericDeclarations.zip(structType.genericsInfo).toMap()
        val actualMemberType = memberTypeDeclaration.substituteGenerics(map = replacementMap)
        return DecoratedExpression.StructMemberAccess(
                structExpr = decoratedStructExpr, memberName = memberName, type = actualMemberType
        )
    }

}

/**
 * [NotExpr] represents the logical inversion of expression [expr].
 */
data class NotExpr(val expr: Expression) : Expression() {

    override fun typeCheck(environment: TypeCheckingEnv): DecoratedExpression {
        val e = expr.typeCheck(environment = environment)
        val t = e.type
        if (t == boolTypeExpr) {
            return DecoratedExpression.Not(expr = e, type = boolTypeExpr)
        } else {
            throw UnexpectedTypeError(expectedType = boolTypeExpr, actualType = t)
        }
    }

}

/**
 * [BinaryExpr] represents a binary expression with operator [op] between [left] and [right].
 */
data class BinaryExpr(
        val left: Expression, val op: BinaryOperator, val right: Expression
) : Expression() {

    override fun typeCheck(environment: TypeCheckingEnv): DecoratedExpression {
        val leftExpr = left.typeCheck(environment = environment)
        val leftType = leftExpr.type
        val rightExpr = right.typeCheck(environment = environment)
        val rightType = rightExpr.type
        val type = when (op) {
            SHL, SHR, USHR, XOR, LAND, LOR, MUL, DIV, MOD, PLUS, MINUS -> {
                // int binary operators
                if (leftType != intTypeExpr) {
                    throw UnexpectedTypeError(
                            expectedType = intTypeExpr, actualType = leftType
                    )
                }
                if (rightType == intTypeExpr) intTypeExpr else {
                    throw UnexpectedTypeError(
                            expectedType = intTypeExpr, actualType = rightType
                    )
                }
            }
            F_MUL, F_DIV, F_PLUS, F_MINUS -> {
                // float binary operators
                if (leftType != floatTypeExpr) {
                    throw UnexpectedTypeError(
                            expectedType = floatTypeExpr, actualType = leftType
                    )
                }
                if (rightType == floatTypeExpr) floatTypeExpr else {
                    throw UnexpectedTypeError(
                            expectedType = floatTypeExpr, actualType = rightType
                    )
                }
            }
            STR_CONCAT -> {
                if (leftType != stringTypeExpr) {
                    throw UnexpectedTypeError(
                            expectedType = stringTypeExpr, actualType = leftType
                    )
                }
                if (leftType == rightType) stringTypeExpr else {
                    throw UnexpectedTypeError(
                            expectedType = leftType, actualType = rightType
                    )
                }
            }
            LT, LE, GT, GE -> {
                // comparison type operator
                when (leftType) {
                    unitTypeExpr, intTypeExpr, floatTypeExpr, boolTypeExpr,
                    charTypeExpr, stringTypeExpr -> {
                        if (leftType == rightType) boolTypeExpr else {
                            throw UnexpectedTypeError(
                                    expectedType = leftType, actualType = rightType
                            )
                        }
                    }
                    else -> throw UnexpectedTypeError(
                            expectedType = intTypeExpr, actualType = leftType
                    )
                }
            }
            STRUCT_EQ, STRUCT_NE -> {
                // equality operator
                if (leftType == rightType) boolTypeExpr else {
                    throw UnexpectedTypeError(
                            expectedType = leftType, actualType = rightType
                    )
                }
            }
            AND, OR -> {
                // conjunction and disjunction
                if (leftType != boolTypeExpr) {
                    throw UnexpectedTypeError(
                            expectedType = boolTypeExpr, actualType = leftType
                    )
                }
                if (leftType == rightType) boolTypeExpr else {
                    throw UnexpectedTypeError(
                            expectedType = leftType, actualType = rightType
                    )
                }
            }
        }
        return DecoratedExpression.Binary(left = leftExpr, op = op, right = rightExpr, type = type)
    }

}

/**
 * [ThrowExpr] represents the throw exception expression, where the thrown exception is [expr].
 * The throw expression is coerced to have [type].
 */
data class ThrowExpr(val type: TypeExpr, val expr: Expression) : Expression() {

    override fun typeCheck(environment: TypeCheckingEnv): DecoratedExpression {
        val e = expr.typeCheck(environment = environment)
        val t = e.type
        if (t != stringTypeExpr) {
            throw UnexpectedTypeError(expectedType = stringTypeExpr, actualType = t)
        }
        return DecoratedExpression.Throw(type = type, expr = e)
    }

}

/**
 * [IfElseExpr] represents the if else expression, guarded by [condition] and having two
 * branches [e1] and [e2].
 */
data class IfElseExpr(
        val condition: Expression, val e1: Expression, val e2: Expression
) : Expression() {

    override fun typeCheck(environment: TypeCheckingEnv): DecoratedExpression {
        val conditionExpr = condition.typeCheck(environment = environment)
        val conditionType = conditionExpr.type
        if (conditionType != boolTypeExpr) {
            throw UnexpectedTypeError(expectedType = boolTypeExpr, actualType = conditionType)
        }
        val decoratedE1 = e1.typeCheck(environment = environment)
        val t1 = decoratedE1.type
        val decoratedE2 = e2.typeCheck(environment = environment)
        val t2 = decoratedE2.type
        if (t1 != t2) {
            throw UnexpectedTypeError(expectedType = t1, actualType = t2)
        }
        return DecoratedExpression.IfElse(
                condition = conditionExpr, e1 = decoratedE1, e2 = decoratedE2, type = t1
        )
    }

}

/**
 * [MatchExpr] represents the pattern matching expression, with a list of [matchingList] to match
 * against [exprToMatch].
 */
data class MatchExpr(
        val exprToMatch: Expression, val matchingList: List<Pair<Pattern, Expression>>
) : Expression() {

    override fun typeCheck(environment: TypeCheckingEnv): DecoratedExpression {
        val decoratedExprToMatch = exprToMatch.typeCheck(environment = environment)
        val typeToMatch = decoratedExprToMatch.type
        val typeIdentifier = (typeToMatch as? TypeExpr.Identifier)
                ?: throw PatternMatchingError.UnmatchableType(typeExpr = typeToMatch)
        val (_, typeDefinition) = environment.typeDefinitions[typeIdentifier.type]
                ?: throw PatternMatchingError.UnmatchableType(typeExpr = typeToMatch)
        val variantTypeDeclarations = (typeDefinition as? TypeDeclaration.Variant)
                ?.map?.toMutableMap()
                ?: throw PatternMatchingError.UnmatchableType(typeExpr = typeToMatch)
        var type: TypeExpr? = null
        val decoratedMatchingList = arrayListOf<Pair<DecoratedPattern, DecoratedExpression>>()
        for ((pattern, expr) in matchingList) {
            if (variantTypeDeclarations.isEmpty()) {
                throw PatternMatchingError.UnusedPattern(pattern = pattern)
            }
            val (decoratedPattern, newEnv) = pattern.typeCheck(
                    typeToMatch = typeToMatch, environment = environment,
                    variantTypeDefs = variantTypeDeclarations
            )
            val decoratedExpr = expr.typeCheck(environment = newEnv)
            decoratedMatchingList.add(decoratedPattern to decoratedExpr)
            val exprType = decoratedExpr.type
            val knownType = type
            if (knownType == null) {
                type = exprType
            } else {
                if (knownType != exprType) {
                    throw UnexpectedTypeError(expectedType = knownType, actualType = exprType)
                }
            }
        }
        if (variantTypeDeclarations.isNotEmpty()) {
            throw PatternMatchingError.NonExhaustive()
        }
        return DecoratedExpression.Match(
                exprToMatch = decoratedExprToMatch, matchingList = decoratedMatchingList,
                type = type ?: throw PatternMatchingError.NonExhaustive()
        )
    }

}

/**
 * [FunctionApplicationExpr] is the function application expression, with [functionExpr] as the
 * function and [arguments] as arguments of the function.
 */
data class FunctionApplicationExpr(
        val functionExpr: Expression, val arguments: List<Expression>
) : Expression() {

    override fun typeCheck(environment: TypeCheckingEnv): DecoratedExpression {
        val decoratedFunctionExpr = functionExpr.typeCheck(environment = environment)
        val functionTypeOpt = decoratedFunctionExpr.type
        val functionType = functionTypeOpt as? TypeExpr.Function
                ?: throw UnexpectedTypeError(
                        expectedType = "<function>", actualType = functionTypeOpt
                )
        val unusedArgs: LinkedList<TypeExpr> = LinkedList(functionType.argumentTypes)
        val decoratedArgumentExpr = arrayListOf<DecoratedExpression>()
        for (expr in arguments) {
            if (unusedArgs.isEmpty()) {
                throw TooManyArgumentsError()
            }
            val expType = unusedArgs.removeFirst()
            val decoratedExpr = expr.typeCheck(environment = environment)
            decoratedArgumentExpr.add(element = decoratedExpr)
            val exprType = decoratedExpr.type
            if (expType != exprType) {
                throw UnexpectedTypeError(expectedType = expType, actualType = exprType)
            }
        }
        val returnType = if (unusedArgs.isEmpty()) functionType.returnType else
            TypeExpr.Function(
                    argumentTypes = ArrayList(unusedArgs), returnType = functionType.returnType
            )
        return DecoratedExpression.FunctionApplication(
                functionExpr = decoratedFunctionExpr, arguments = decoratedArgumentExpr,
                type = returnType
        )
    }
}


/**
 * [FunctionExpr] is the function expression with some [arguments] and the function [body].
 */
data class FunctionExpr(
        val arguments: List<Pair<String, TypeExpr>>, val body: Expression
) : Expression() {

    override fun typeCheck(environment: TypeCheckingEnv): DecoratedExpression {
        val newEnv = environment.copy(
                normalTypeEnv = arguments.fold(initial = environment.normalTypeEnv) { e, (n, t) ->
                    e.put(key = n, value = t)
                }
        )
        val bodyExpr = body.typeCheck(environment = newEnv)
        val bodyType = bodyExpr.type
        val functionDeclaredType = TypeExpr.Function(
                argumentTypes = arguments.map { it.second }, returnType = bodyType
        )
        functionDeclaredType.checkTypeValidity(environment = environment)
        return DecoratedExpression.Function(
                arguments = arguments, returnType = bodyType, body = bodyExpr,
                type = functionDeclaredType
        )
    }

}

/**
 * [TryCatchExpr] represents the try catch finally structure as an expression, where the
 * [tryExpr] is evaluated, and guard by catch branch with [exception] in scope and [catchHandler]
 * to deal with it.
 */
data class TryCatchExpr(
        val tryExpr: Expression, val exception: String, val catchHandler: Expression
) : Expression() {

    override fun typeCheck(environment: TypeCheckingEnv): DecoratedExpression {
        val decoratedTryExpr = tryExpr.typeCheck(environment = environment)
        val tryType = decoratedTryExpr.type
        val decoratedCatchExpr = catchHandler.typeCheck(
                environment.copy(normalTypeEnv = environment.normalTypeEnv.put(
                        key = exception, value = stringTypeExpr
                ))
        )
        val catchType = decoratedCatchExpr.type
        if (tryType != catchType) {
            throw UnexpectedTypeError(expectedType = tryType, actualType = catchType)
        }
        return DecoratedExpression.TryCatch(
                tryExpr = decoratedTryExpr, exception = exception,
                catchHandler = decoratedCatchExpr, type = tryType
        )
    }

}

/**
 * [LetExpr] represents the let expression of the form
 * `let` [identifier] `=` [e1] `;` [e2]
 */
data class LetExpr(
        val identifier: String, val e1: Expression, val e2: Expression
) : Expression() {

    override fun typeCheck(environment: TypeCheckingEnv): DecoratedExpression =
            if (environment.normalTypeEnv[identifier] == null) {
                val decoratedE1 = e1.typeCheck(environment = environment)
                val newEnv = environment.copy(normalTypeEnv = environment.normalTypeEnv.put(
                        key = identifier, value = decoratedE1.type
                ))
                val decoratedE2 = e2.typeCheck(environment = newEnv)
                val e2Type = decoratedE2.type
                DecoratedExpression.Let(
                        identifier = identifier, e1 = decoratedE1, e2 = decoratedE2, type = e2Type
                )
            } else {
                throw IdentifierError.ShadowedName(shadowedName = identifier)
            }

}
