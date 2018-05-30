package com.developersam.pl.sapl.typecheck

import com.developersam.fp.FpMap
import com.developersam.pl.sapl.ast.FunctionTypeInAnnotation
import com.developersam.pl.sapl.ast.Module
import com.developersam.pl.sapl.ast.SingleIdentifierTypeInAnnotation
import com.developersam.pl.sapl.ast.TypeInformation
import com.developersam.pl.sapl.exceptions.CompileTimeError
import com.developersam.pl.sapl.exceptions.UnexpectedTypeError
import com.developersam.pl.sapl.util.toFunctionTypeExpr

/**
 * [TypeChecker] defines a type checker that type checks modules.
 */
internal object TypeChecker {

    /**
     * [typeCheck] tries to type check the given top-level [module].
     *
     * If it does not type check, it will throw an [CompileTimeError]
     */
    fun typeCheck(module: Module) {
        typeCheckModule(module = module, e = TypeCheckerEnv(
                typeDefinitions = FpMap.empty(),
                currentLevelTypeEnv = FpMap.empty(),
                upperLevelTypeEnv = FpMap.empty()
        ))
    }

    /**
     * [typeCheckModule] tries to type check a [module] under the given [TypeCheckerEnv] [e] and
     * returns a new environment after type check.
     */
    private fun typeCheckModule(e: TypeCheckerEnv, module: Module): TypeCheckerEnv {
        // conflict checker
        val members = module.members
        members.noNameShadowingValidation()
        // processed type declarations
        val init = TypeCheckerEnv(
                typeDefinitions = members.typeMembers.fold(e.typeDefinitions) { acc, m ->
                    acc.put(key = m.identifier, value = m.declaration)
                },
                upperLevelTypeEnv = e.upperLevelTypeEnv,
                currentLevelTypeEnv = FpMap.empty()
        )
        // process constant definitions
        val eWithC = members.constantMembers.fold(init) { env, m ->
            env.updateTypeInfo(variable = m.identifier, typeInfo = m.expr.inferType(env))
        }
        // process function definitions
        val eWithF = eWithC.updateCurrent(
                newValue = members.functionMembers.fold(eWithC.currentLevelTypeEnv) { env, m ->
                    env.put(key = m.identifier, value = TypeInformation(
                            typeExpr = toFunctionTypeExpr(
                                    argumentTypes = m.arguments.map { it.second },
                                    returnType = m.returnType
                            ),
                            genericInfo = m.genericsDeclaration
                    ))
                })
        members.functionMembers.forEach { m ->
            val expectedType = eWithF.getTypeInfo(variable = m.identifier)
                    ?.typeExpr
                    ?.let {
                        when (it) {
                            is SingleIdentifierTypeInAnnotation -> error(message = "Impossible")
                            is FunctionTypeInAnnotation -> TypeInformation(
                                    typeExpr = it.returnType, genericInfo = m.genericsDeclaration
                            )
                        }
                    }!!
            val bodyType = m.body.inferType(environment = eWithF)
            if (expectedType != bodyType) {
                throw UnexpectedTypeError(expectedType = expectedType, actualType = bodyType)
            }
        }
        // process nested modules
        val envWithModules = members.nestedModuleMembers.fold(eWithF, ::typeCheckModule)
        // remove private members
        var envTemp = members.constantMembers.fold(envWithModules) { env, m ->
            if (m.isPublic) env else env.removeTypeInfo(variable = m.identifier)
        }
        envTemp = members.functionMembers.fold(envTemp) { env, m ->
            if (m.isPublic) env else env.removeTypeInfo(variable = m.identifier)
        }
        // move current level to upper level
        val newUpperLevel = envTemp.currentLevelTypeEnv
                .mapByKey { k -> "${module.name}.$k" }
                .reduce(envTemp.upperLevelTypeEnv) { k, v, acc -> acc.put(key = k, value = v) }
        return envTemp.copy(upperLevelTypeEnv = newUpperLevel, currentLevelTypeEnv = FpMap.empty())
    }

}