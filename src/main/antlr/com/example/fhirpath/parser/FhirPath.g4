grammar FhirPath;

parse : expression EOF ;

expression
    : additiveExpr
    ;

additiveExpr
    : primary (('+' | '-') primary)*
    ;

primary
    : literal
    | functionCall
    | identifier
    ;

functionCall
    : identifier '(' (expression (',' expression)*)? ')'
    ;

literal
    : INT
    | STRING
    ;

identifier
    : IDENTIFIER
    ;

INT        : [0-9]+ ;
STRING     : '\'' (~['\r\n])* '\'' ;
IDENTIFIER : [a-zA-Z_][a-zA-Z0-9_]* ;
WS         : [ \t\r\n]+ -> skip ;

