cs323@deb-cs323-compilers:~/Desktop/Sustech2025_Compile-project4/CS323-Compilers-2025F-Projects-project4-base$ javac -cp libs/antlr-4.13.2-complete.jar \
      -d out \
      $(find src/main/java -name "*.java")
cs323@deb-cs323-compilers:~/Desktop/Sustech2025_Compile-project4/CS323-Compilers-2025F-Projects-project4-base$ java -cp "out:libs/antlr-4.13.2-complete.jar" Main
Line 6: Unexpected Type: for operator =, lhs: int, rhs: int[1]
Line 7: Unexpected Type: for operator =, lhs: int[10][20], rhs: int[10][20]
Line 9: Unexpected Type: int[10][20]
Variables:
arr: int[30][20][10]

Functions:
main: int()
cs323@deb-cs323-compilers:~/Desktop/Sustech2025_Compile-project4/CS323-Compilers-2025F-Projects-project4-base$ javac -cp libs/antlr-4.13.2-complete.jar \
      -d out \
      $(find src/main/java -name "*.java")
cs323@deb-cs323-compilers:~/Desktop/Sustech2025_Compile-project4/CS323-Compilers-2025F-Projects-project4-base$ java -cp "out:libs/antlr-4.13.2-complete.jar" Main
Line 7: Unexpected Type: int[5]
Line 8: lvalue: lvalue is required.
Line 10: Unexpected Type: for operator =, lhs: int, rhs: int[5]
Line 11: Unexpected Type: for operator =, lhs: int[11], rhs: int
Variables:
X: int[5]

Functions:
get_int: int()
main: int()
cs323@deb-cs323-compilers:~/Desktop/Sustech2025_Compile-project4/CS323-Compilers-2025F-Projects-project4-base$ javac -cp libs/antlr-4.13.2-complete.jar \
      -d out \
      $(find src/main/java -name "*.java")
cs323@deb-cs323-compilers:~/Desktop/Sustech2025_Compile-project4/CS323-Compilers-2025F-Projects-project4-base$ java -cp "out:libs/antlr-4.13.2-complete.jar" Main
line 20:4 missing ';' at 'im_a_function'
Line 10: badIdent: identifier is not a function: im_a_variable
Line 11: badIdent: identifier is not a variable: im_a_function
Line 13: Unexpected Type: for operator =, lhs: int[10], rhs: int
Line 14: Unexpected Type: int[10]
Line 16: Unexpected Type: for operator ==, lhs: int[10][20], rhs: int[10]
Line 17: lvalue: lvalue is required.
Line 19: badCall: param count mismatch, requires: 2, given: 3
Line 20: badCall: 2-th param mismatch
Line 22: Unexpected Type: int[10][20]
Variables:
im_a_variable: int

Functions:
im_a_function: int(int,int)
main0: int()
cs323@deb-cs323-compilers:~/Desktop/Sustech2025_Compile-project4/CS323-Compilers-2025F-Projects-project4-base$ javac -cp libs/antlr-4.13.2-complete.jar \
      -d out \
      $(find src/main/java -name "*.java")
cs323@deb-cs323-compilers:~/Desktop/Sustech2025_Compile-project4/CS323-Compilers-2025F-Projects-project4-base$ java -cp "out:libs/antlr-4.13.2-complete.jar" Main
Line 9: Unexpected Type: for operator =, lhs: int[10], rhs: struct s0
Line 11: Unexpected Type: int[10][20]
Variables:

Functions:
main: int()
cs323@deb-cs323-compilers:~/Desktop/Sustech2025_Compile-project4/CS323-Compilers-2025F-Projects-project4-base$ javac -cp libs/antlr-4.13.2-complete.jar \
      -d out \
      $(find src/main/java -name "*.java")
cs323@deb-cs323-compilers:~/Desktop/Sustech2025_Compile-project4/CS323-Compilers-2025F-Projects-project4-base$ java -cp "out:libs/antlr-4.13.2-complete.jar" Main
Line 12: lvalue: lvalue is required.
Variables:

Functions:
main0: int()
cs323@deb-cs323-compilers:~/Desktop/Sustech2025_Compile-project4/CS323-Compilers-2025F-Projects-project4-base$ 
