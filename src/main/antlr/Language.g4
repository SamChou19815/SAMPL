/**
 * Grammar for SAPL
 */
grammar Language;

/** The start rule; begin parsing here. */
compilationUnit : importDeclaration? classTypeDeclaration classMemberDeclaration+ EOF;

importDeclaration : IMPORT LBRACE UpperIdentifier (COMMA UpperIdentifier)* RBRACE;

classMemberDeclaration : classConstantDeclaration | classMethodDeclaration;

classTypeDeclaration : PRIVATE? TYPE typeIdentifier ASSIGN typeValue;

typeValue
    : nestedTypeValue # NestedType
    | tupleTypeValue # TupleType
    | variantTypeValue # VariantType
    | structTypeValue # StructType
    | functionTypeValue # FunctionType
    ;

nestedTypeValue : LPAREN typeValue RPAREN;

tupleTypeValue : typeIdentifier (MUL typeIdentifier)*;

variantTypeValue : (VARIANT_OR UpperIdentifier (OF typeValue)?)+;

structTypeValue : LBRACE annotatedVariable (SEMICOLON annotatedVariable)* RBRACE;

functionTypeValue : typeIdentifier ARROW typeIdentifier;

classConstantDeclaration : PRIVATE? CONST LowerIdentifier ASSIGN expression;

classMethodDeclaration :
    PRIVATE? FUNCTION LowerIdentifier
        genericsDeclaration? argumentsDeclaration typeAnnotation
    ASSIGN expression;

expression
    : LPAREN expression RPAREN # NestedExpr
    | Literal # LiteralExpr
    | LowerIdentifier # IdentifierExpr
    | UpperIdentifier DOT LowerIdentifier # IdentifierInModuleExpr
    | expression (LPAREN expression+ RPAREN) # FunctionApplicationExpr
    | expression BitOperator expression # BitExpr
    | expression FactorOperator expression # FactorExpr
    | expression TermOperator expression # TermExpr
    | expression STR_CONCAT expression # StringConcatExpr
    | expression BinaryLogicalOperator expression # BooleanExpr
    | expression ComparisonOperator expression # ComparisonExpr
    | NOT expression # NotExpr
    | LET pattern typeAnnotation? ASSIGN expression SEMICOLON expression # LetExpr
    | FUNCTION genericsDeclaration? argumentsDeclaration ARROW expression # LambdaExpr
    | IF expression THEN expression ELSE expression # IfElseExpr
    | MATCH LowerIdentifier WITH (VARIANT_OR pattern ARROW expression)+ # MatchExpr
    | THROW expression # ThrowExpr
    | TRY expression CATCH LowerIdentifier expression (FINALLY expression)? # TryCatchFinallyExpr
    ;

pattern
    : UNIT # UnitPattern
    | typeIdentifier LowerIdentifier # VariantPattern
    | LowerIdentifier # VariablePattern
    | WILDCARD # WildcardPattern
    ;

annotatedVariable : LowerIdentifier typeAnnotation;

typeAnnotation : COLON (typeIdentifier | typeValue);

typeIdentifier : UpperIdentifier genericsDeclaration?;

argumentsDeclaration : (UNIT | (LPAREN annotatedVariable RPAREN)*);

genericsDeclaration : LT UpperIdentifier (COMMA UpperIdentifier)* GT;

// KEYWORDS

IMPORT : 'import';

CLASS : 'class';

TYPE : 'type';

OF : 'of';

LET : 'let';

CONST : 'const';

FUNCTION : 'function';

PRIVATE : 'private';

IF : 'if';

THEN : 'then';

ELSE : 'else';

MATCH : 'match';

WITH : 'with';

THROW : 'throw';

TRY : 'try';

CATCH : 'catch';

FINALLY : 'finally';

UNIT : '()';

WILDCARD : '_';

// PARENTHESIS

LPAREN : '(';

RPAREN : ')';

LBRACE : '{';

RBRACE : '}';

LBRACKET : '[';

RBRACKER : ']';

// SEPARATORS

SEMICOLON : ';';

COLON : ':';

COMMA : ',';

DOT : '.';

ARROW : '->';

VARIANT_OR : '|';

// OPERATORS

STR_CONCAT : '^';

BitOperator : SHL | SHR | USHR | XOR | LAND | LOR;

SHL : '<<';

SHR : '>>';

USHR : '>>>';

XOR : 'xor';

LAND : '&';

LOR : '|';

FactorOperator : MUL | DIV | MOD | F_MUL | F_DIV;

MUL : '*';

DIV : '/';

MOD : '%';

F_MUL : '*.';

F_DIV : '/';

TermOperator : PLUS | MINUS | F_PLUS | F_MINUS;

PLUS : '+';

MINUS : '-';

F_PLUS : '+.';

F_MINUS : '-.';

BinaryLogicalOperator : AND | OR;

AND : '&&';

OR : '||';

NOT : '!';

ASSIGN : '=';

ComparisonOperator : REF_EQ | STRUCT_EQ | LT | LE | GT | GE;

REF_EQ : '===';

STRUCT_EQ : '==';

LT : '<';

LE : '<=';

GT : '>';

GE : '>=';

// LITERALS

Literal
    :   IntegerLiteral
    |   FloatingPointLiteral
    |   CharacterLiteral
    |   StringLiteral
    |   BooleanLiteral
    ;

IntegerLiteral : HexLiteral | OctalLiteral | DecimalLiteral;

BooleanLiteral : 'true' | 'false';

HexLiteral : '0' ('x'|'X') HexDigit+ IntegerTypeSuffix? ;

DecimalLiteral : ('0' | '1'..'9' '0'..'9'*) IntegerTypeSuffix? ;

OctalLiteral : '0' ('0'..'7')+ IntegerTypeSuffix? ;

fragment
HexDigit : ('0'..'9'|'a'..'f'|'A'..'F') ;

fragment
IntegerTypeSuffix : ('l'|'L') ;

FloatingPointLiteral
    :   ('0'..'9')+ '.' ('0'..'9')* Exponent? FloatTypeSuffix?
    |   '.' ('0'..'9')+ Exponent? FloatTypeSuffix?
    |   ('0'..'9')+ Exponent FloatTypeSuffix?
    |   ('0'..'9')+ FloatTypeSuffix
    |   ('0x' | '0X') (HexDigit )*
        ('.' (HexDigit)*)?
        ( 'p' | 'P' )
        ( '+' | '-' )?
        ( '0' .. '9' )+
        FloatTypeSuffix?
    ;

fragment
Exponent : ('e'|'E') ('+'|'-')? ('0'..'9')+ ;

fragment
FloatTypeSuffix : ('f'|'F'|'d'|'D') ;

CharacterLiteral
    :   '\'' ( EscapeSequence | ~('\''|'\\') ) '\''
    ;

StringLiteral
    :  '"' ( EscapeSequence | ~('\\'|'"') )* '"'
    ;

fragment
EscapeSequence
    :   '\\' ('b'|'t'|'n'|'f'|'r'|'"'|'\''|'\\')
    |   UnicodeEscape
    |   OctalEscape
    ;

fragment
OctalEscape
    :   '\\' ('0'..'3') ('0'..'7') ('0'..'7')
    |   '\\' ('0'..'7') ('0'..'7')
    |   '\\' ('0'..'7')
    ;

fragment
UnicodeEscape
    :   '\\' 'u' HexDigit HexDigit HexDigit HexDigit
    ;

LowerIdentifier : LowerLetter (Letter | Digit)*;

UpperIdentifier : UpperLetter (Letter | Digit)*;

Letter : LowerLetter | UpperLetter;

LowerLetter : 'a'..'z';

UpperLetter : 'A'..'Z';

fragment
Digit : NonZeroDigit | ZeroDigit;

NonZeroDigit : '1'..'9';

ZeroDigit : '0';

// COMMENT

COMMENT
    :   '/*' .*? '*/'    -> channel(HIDDEN) // match anything between /* and */
    ;
WS  :   [ \r\t\u000C\n]+ -> channel(HIDDEN)
    ;

LINE_COMMENT
    : '//' ~[\r\n]* '\r'? '\n' -> channel(HIDDEN)
    ;
