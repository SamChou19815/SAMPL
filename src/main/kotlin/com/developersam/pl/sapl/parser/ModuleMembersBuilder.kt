package com.developersam.pl.sapl.parser

import com.developersam.pl.sapl.antlr.PLBaseVisitor
import com.developersam.pl.sapl.antlr.PLParser.ModuleMembersDeclarationContext
import com.developersam.pl.sapl.ast.ModuleConstantMember
import com.developersam.pl.sapl.ast.ModuleFunctionMember
import com.developersam.pl.sapl.ast.ModuleTypeMember
import com.developersam.pl.sapl.ast.ModuleMembers as M

/**
 * [ModuleMembersBuilder] builds module members into AST.
 */
internal object ModuleMembersBuilder : PLBaseVisitor<M>() {

    override fun visitModuleMembersDeclaration(ctx: ModuleMembersDeclarationContext): M {
        val typeMembers = ctx.moduleTypeDeclaration().map { c ->
            ModuleTypeMember(
                    identifier = c.typeIdentifier().accept(TypeIdentifierBuilder),
                    declaration = c.typeExprInDeclaration().accept(TypeExprInDeclarationBuilder)
            )
        }
        val constantMembers = ctx.moduleConstantDeclaration().map { c ->
            ModuleConstantMember(
                    isPublic = c.PRIVATE() == null,
                    identifier = c.LowerIdentifier().text,
                    expr = c.expression().accept(ExprBuilder)
            )
        }
        val functionMembers = ctx.moduleFunctionDeclaration().map { c ->
            ModuleFunctionMember(
                    isPublic = c.PRIVATE() == null,
                    identifier = c.LowerIdentifier().text,
                    genericsDeclaration = c.genericsDeclaration()
                            ?.accept(GenericsDeclarationBuilder) ?: emptySet(),
                    arguments = c.argumentDeclaration()
                            .map { it.accept(ArgumentDeclarationBuilder) },
                    returnType = c.typeAnnotation().typeExprInAnnotation()
                            .accept(TypeExprInAnnotationBuilder),
                    body = c.expression().accept(ExprBuilder)
            )
        }
        val nestedModuleMembers = ctx.moduleDeclaration().map { it.accept(ModuleBuilder) }
        return M(
                typeMembers = typeMembers, constantMembers = constantMembers,
                functionMembers = functionMembers, nestedModuleMembers = nestedModuleMembers
        )
    }

}