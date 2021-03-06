package org.sampl.environment

import com.developersam.fp.FpMap
import org.sampl.TOP_LEVEL_PROGRAM_NAME
import org.sampl.ast.common.FunctionCategory
import org.sampl.ast.raw.ClassFunction
import org.sampl.ast.raw.ClassMember
import org.sampl.ast.type.TypeDeclaration
import org.sampl.ast.type.TypeExpr
import org.sampl.ast.type.TypeInfo
import org.sampl.ast.type.boolTypeId
import org.sampl.ast.type.charTypeId
import org.sampl.ast.type.floatTypeId
import org.sampl.ast.type.intTypeId
import org.sampl.ast.type.stringArrayTypeId
import org.sampl.ast.type.stringTypeId
import org.sampl.ast.type.unitTypeId

/**
 * [TypeCheckingEnv] is the environment for type checking. It contains a set of currently
 * determined definitions to help type check the program.
 *
 * @param typeDefinitions the set that maps type identifiers to actual types.
 * @param declaredTypes the set of declared types with correctly qualified identifiers.
 * @param classFunctionTypeEnv the type environment with correctly qualified identifiers for
 * functions.
 * @param normalTypeEnv the type environment with correctly qualified identifiers for normal values.
 */
internal data class TypeCheckingEnv(
        val typeDefinitions: FpMap<String, Pair<List<String>, TypeDeclaration>> = FpMap.empty(),
        val declaredTypes: FpMap<String, List<String>> = FpMap.empty(),
        val classFunctionTypeEnv: FpMap<String, TypeInfo> = FpMap.empty(),
        val normalTypeEnv: FpMap<String, TypeExpr> = FpMap.empty()
) {

    /**
     * [enterClass] produces a new [TypeCheckingEnv] with all the public information preserved and
     * make all the types declared in the class available. Type checking is not done here.
     */
    fun enterClass(clazz: ClassMember.Clazz): TypeCheckingEnv = TypeCheckingEnv(
            typeDefinitions = typeDefinitions.put(
                    key = clazz.identifier.name,
                    value = clazz.identifier.genericsInfo to clazz.declaration
            ),
            declaredTypes = declaredTypes.put(
                    key = clazz.identifier.name, value = clazz.identifier.genericsInfo
            ),
            classFunctionTypeEnv = classFunctionTypeEnv,
            normalTypeEnv = normalTypeEnv
    )

    /**
     * [ClassMember.Constant.processWhenExit] returns a new type environment when exiting a
     * class given [currentTypeEnv], [className], and [subclassNames] for reference.
     * It needs to remove all private members and prefix each member and its type by the class name.
     */
    private fun ClassMember.Constant.processWhenExit(
            currentTypeEnv: FpMap<String, TypeExpr>, className: String, subclassNames: List<String>
    ): FpMap<String, TypeExpr> =
            if (isPublic) {
                val oldType = currentTypeEnv[identifier]
                        ?: error(message = "Impossible. Name: $identifier")
                val newType = subclassNames.fold(initial = oldType) { t, n ->
                    t.toPrefixed(typeToPrefix = n, prefix = className)
                }
                currentTypeEnv.remove(identifier).put("$className.$identifier", newType)
            } else currentTypeEnv.remove(identifier)

    /**
     * [ClassFunction.processWhenExit] returns a new type environment when exiting a class given
     * [currentFunEnv], [className], and [subclassNames] for reference.
     * It needs to remove all private members and prefix each member and its type by the class name.
     */
    private fun ClassFunction.processWhenExit(
            currentFunEnv: FpMap<String, TypeInfo>, className: String, subclassNames: List<String>
    ): FpMap<String, TypeInfo> = when {
        category != FunctionCategory.USER_DEFINED -> currentFunEnv
        isPublic -> {
            var v = currentFunEnv[identifier] ?: error(message = "Impossible. Name: $identifier")
            val newT = subclassNames.fold(initial = v.typeExpr) { t, n ->
                t.toPrefixed(typeToPrefix = n, prefix = className)
            }
            v = v.copy(typeExpr = newT)
            currentFunEnv.remove(key = identifier).put(key = "$className.$identifier", value = v)
        }
        else -> currentFunEnv.remove(key = identifier)
    }

    /**
     * [ClassMember.Clazz.processAsSubclassWhenExit] returns a new type environment when exiting
     * a class given [currentEnv], [className], and [subclassNames] for reference.
     * It needs to prefix the class type with this class name and also all its child members.
     */
    private fun ClassMember.Clazz.processAsSubclassWhenExit(
            currentEnv: TypeCheckingEnv, className: String, subclassNames: List<String>
    ): TypeCheckingEnv {
        val subclassName = identifier.name
        val newDeclaredTypes = currentEnv.declaredTypes.asSequence()
                // when exiting, we need to use fully qualified name.
                .filter { (name, _) -> name in subclassNames }
                .fold(initial = currentEnv.declaredTypes) { dec, (name, genericsInfo) ->
                    dec.remove(key = name).put("$className.$name", genericsInfo)
                }
        // Also prefix each class constant member of the subclass with the classname.
        val newNormalTypeEnv = currentEnv.normalTypeEnv.mapByKeyValuePair { s, typeExpr ->
            if (s.indexOf(subclassName) == 0) {
                // prefixed with name, need to prefix!
                val newT = typeExpr.toPrefixed(typeToPrefix = subclassName, prefix = className)
                "$className.$s" to newT
            } else s to typeExpr
        }
        // Do the same thing for functions
        val newFunctionTypeEnv = currentEnv.classFunctionTypeEnv.mapByKeyValuePair { s, tInfo ->
            if (s.indexOf(subclassName) == 0) {
                // prefixed with name, need to prefix!
                val newTypeInfo = tInfo.copy(typeExpr = tInfo.typeExpr.toPrefixed(
                        typeToPrefix = subclassName, prefix = className
                ))
                "$className.$s" to newTypeInfo
            } else s to tInfo
        }
        return currentEnv.copy(
                declaredTypes = newDeclaredTypes,
                normalTypeEnv = newNormalTypeEnv,
                classFunctionTypeEnv = newFunctionTypeEnv
        )
    }

    /**
     * [exitClass] produces a new [TypeCheckingEnv] with all the public information preserved and
     * make the access of current class elements prefixed with class name.
     */
    fun exitClass(clazz: ClassMember.Clazz): TypeCheckingEnv {
        val className = clazz.identifier.name
        val subclassNames = clazz.members.mapNotNull { m ->
            (m as? ClassMember.Clazz)?.identifier?.name
        }
        // prefix member name
        val currentEnv = clazz.members.fold(initial = this) { e, member ->
            when (member) {
                is ClassMember.Constant -> e.copy(normalTypeEnv = member.processWhenExit(
                        currentTypeEnv = e.normalTypeEnv, className = className,
                        subclassNames = subclassNames
                ))
                is ClassMember.FunctionGroup -> {
                    val funEnv = member.functions.fold(initial = e.classFunctionTypeEnv) { env, f ->
                        f.processWhenExit(
                                currentFunEnv = env, className = className,
                                subclassNames = subclassNames
                        )
                    }
                    e.copy(classFunctionTypeEnv = funEnv)
                }
                is ClassMember.Clazz -> member.processAsSubclassWhenExit(
                        currentEnv = e, className = className, subclassNames = subclassNames
                )
            }
        }
        val newTypeDef = subclassNames.fold(currentEnv.typeDefinitions) { d, n -> d.remove(n) }
        return currentEnv.copy(typeDefinitions = newTypeDef)
    }

    companion object {
        /**
         * [initial] is the initial [TypeCheckingEnv] with predefined types includes.
         */
        @JvmField
        val initial: TypeCheckingEnv = TypeCheckingEnv(
                declaredTypes = FpMap.create(
                        TOP_LEVEL_PROGRAM_NAME to emptyList(),
                        unitTypeId.name to unitTypeId.genericsInfo,
                        intTypeId.name to intTypeId.genericsInfo,
                        floatTypeId.name to floatTypeId.genericsInfo,
                        boolTypeId.name to boolTypeId.genericsInfo,
                        charTypeId.name to charTypeId.genericsInfo,
                        stringTypeId.name to stringTypeId.genericsInfo,
                        stringArrayTypeId.name to stringArrayTypeId.genericsInfo
                )
        )
    }

}
