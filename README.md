cs323@deb-cs323-compilers:~/Desktop/Sustech2025_Compile-project4/CS323-Compilers-2025F-Projects-project4-base$ javac -cp libs/antlr-4.13.2-complete.jar \
      -d out \
      $(find src/main/java -name "*.java")
src/main/java/impl/Compiler.java:836: 错误: 找不到符号
                List<Type> params = funcType.paramTypes;
                                    ^
  符号:   变量 funcType
  位置: 类 Compiler.SemanticAnalyzer
src/main/java/impl/Compiler.java:850: 错误: 找不到符号
                return new ExprResult(funcType.returnType, false, false);
                                      ^
  符号:   变量 funcType
  位置: 类 Compiler.SemanticAnalyzer
src/main/java/impl/Compiler.java:905: 错误: 程序包st不存在
                Type memType = st.members.get(memberName);
                                 ^
src/main/java/impl/Compiler.java:907: 错误: 找不到符号
                    Project4SemanticError.badMember(ctx, st, memberName).throwException();
                                                         ^
  符号:   变量 st
  位置: 类 Compiler.SemanticAnalyzer
src/main/java/impl/Compiler.java:926: 错误: 程序包st不存在
                Type memType = st.members.get(memberName);
                                 ^
src/main/java/impl/Compiler.java:928: 错误: 找不到符号
                    Project4SemanticError.badMember(ctx, st, memberName).throwException();
                                                         ^
  符号:   变量 st
  位置: 类 Compiler.SemanticAnalyzer
src/main/java/impl/Compiler.java:995: 错误: 找不到符号
                        return new ExprResult(pt.elementType, true, false);
                                              ^
  符号:   变量 pt
  位置: 类 Compiler.SemanticAnalyzer
7 个错误
cs323@deb-cs323-compilers:~/Desktop/Sustech2025_Compile-project4/CS323-Compilers-2025F-Projects-project4-base$ 
