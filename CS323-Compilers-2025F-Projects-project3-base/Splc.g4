grammar Splc;

// IDEA Plugin Settings
// - Output Directory: src/main/java/
// - package name: generated.Splc

// =========================
// Parser Rules
// =========================

program
    : globalDef* EOF
    ;

// 1. 全局定义(globalDef)

globalDef
    : specifier Identifier LPAREN funcArgs RPAREN LBRACE statement* RBRACE  # FuncDef
    | specifier Identifier LPAREN funcArgs RPAREN SEMI                      # FuncDecl   // 新增
    | specifier varDec SEMI                                                 # GlobalVarDef
    | specifier SEMI                                                        # GlobalStructDecl
    ;

// 2. 类型说明符(specifier)（扩展1：结构体支持）
specifier
    : INT                                                                   # IntSpec
    | CHAR                                                                  # CharSpec
    | STRUCT Identifier                                                     # StructDeclSpec
    | STRUCT Identifier LBRACE (specifier varDec SEMI)* RBRACE              # FullStructSpec
    ;

// 3. 变量声明(varDec)（扩展2：指针支持，[]优先级高于*）
varDec
    : Identifier                                                            # SimpleVar
    | varDec LBRACK Number RBRACK                                           # ArrayVar
    | STAR varDec                                                           # PointerVar
    | LPAREN varDec RPAREN                                                  # ParenVar
    ;

// 4. 函数参数(funcArgs)
funcArgs
    : (specifier varDec (COMMA specifier varDec)*)?
    ;

// 5. 语句(statement)
statement
    : LBRACE statement* RBRACE                                              # BlockStmt
    | specifier varDec (ASSIGN expression)? SEMI                            # VarDecStmt
    | IF LPAREN expression RPAREN statement (ELSE statement)?               # IfStmt
    | WHILE LPAREN expression RPAREN statement                              # WhileStmt
    | RETURN expression SEMI                                                # ReturnStmt
    | expression SEMI                                                       # ExprStmt
    ;

// 6. 表达式（按优先级拆分，替换原来的左递归 expression 规则）
expression
    // 基础表达式
    : Identifier                                                   // 标识符
    | Number                                                       // 数字常量
    | Char                                                         // 字符常量
    | LPAREN expression RPAREN                                     // 括号表达式

    // 后缀运算符（左结合，最高优先级）
    | Identifier LPAREN (expression (COMMA expression)*)? RPAREN   // 函数调用
    | expression LBRACK expression RBRACK                          // 数组访问
    | expression DOT Identifier                                    // 结构体成员访问（扩展1）
    | expression ARROW Identifier                                  // 结构体指针成员访问（扩展1）
    | expression INC                                               // 后缀自增
    | expression DEC                                               // 后缀自减

    // 前缀运算符（右结合）
    | INC expression                                               // 前缀自增
    | DEC expression                                               // 前缀自减
    | PLUS expression                                              // 一元正号
    | MINUS expression                                             // 一元负号
    | NOT expression                                               // 逻辑非
    | AMP expression                                               // 取地址（扩展2）
    | STAR expression                                              // 解引用（扩展2）

    // 二元运算符（按优先级从高到低）
    | expression (STAR | DIV | MOD) expression                     // 乘法、除法、取模（左结合）
    | expression (PLUS | MINUS) expression                         // 加法、减法（左结合）
    | expression (LT | LE | GT | GE) expression                    // 关系运算符（左结合）
    | expression (EQ | NEQ) expression                             // 相等性运算符（左结合）
    | expression AND expression                                    // 逻辑与（左结合）
    | expression OR expression                                     // 逻辑或（左结合）
    | <assoc=right> expression ASSIGN expression                   // 赋值（右结合）
    ;

// =========================
// Lexer Rules
// =========================

// ---------- Keywords ----------
INT     : 'int';
CHAR    : 'char';
STRUCT  : 'struct';
RETURN  : 'return';
IF      : 'if';
ELSE    : 'else';
WHILE   : 'while';

// ---------- Operators ----------
ASSIGN  : '=';
PLUS    : '+';
MINUS   : '-';
STAR    : '*';
DIV     : '/';
MOD     : '%';
LT      : '<';
LE      : '<=';
GT      : '>';
GE      : '>=';
EQ      : '==';
NEQ     : '!=';
AND     : '&&';
OR      : '||';
NOT     : '!';
INC     : '++';
DEC     : '--';
DOT     : '.';
ARROW   : '->';
AMP     : '&';

// ---------- Separators ----------
SEMI    : ';';
COMMA   : ',';
LPAREN  : '(';
RPAREN  : ')';
LBRACE  : '{';
RBRACE  : '}';
LBRACK  : '[';
RBRACK  : ']';

// ---------- Identifiers & Literals ----------
Identifier  : [a-zA-Z_][a-zA-Z0-9_]*;
Number      : '0' | [1-9][0-9]*;
Char        : '\'' (EscapeSeq | ~['\\]) '\'';

fragment EscapeSeq
    : '\\' ('n' | 't' | '\'' | '\\' | '0')
    ;

// ---------- Whitespace & Comments ----------
WS              : [ \t\r\n]+ -> skip;
LINE_COMMENT    : '//' ~[\r\n]* -> skip;
BLOCK_COMMENT   : '/*' .*? '*/' -> skip;
