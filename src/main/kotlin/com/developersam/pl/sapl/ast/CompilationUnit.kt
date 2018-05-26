package com.developersam.pl.sapl.ast

/**
 * [CompilationUnit] represents the top level node in the compiler.
 * It refers to a source code file, which contains some [imports] and module [members].
 */
data class CompilationUnit(val imports: Set<String>, val members: List<ModuleMember>)