package impl.project2;

import framework.project2.Grader;
import framework.project2.MissingSymbolError;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.misc.ParseCancellationException;

public class Project2ErrorListener extends BaseErrorListener {
    private final Grader grader;
    
    public Project2ErrorListener(Grader grader) {
        this.grader = grader;
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, 
                          int line, int charPositionInLine, String msg, RecognitionException e) {
        
        String missingSymbolName = extractMissingSymbolName(msg);   
        
        MissingSymbolError missingSymbol = new MissingSymbolError(missingSymbolName, 0);
        this.grader.getWriter().println(missingSymbol);
        throw new ParseCancellationException();
    }
    
    private String extractMissingSymbolName(String msg) {

        int missingIndex = msg.indexOf("missing");
        if (missingIndex != -1) {
            // 找到 "missing" 后的第一个单引号
            int firstQuote = msg.indexOf("'", missingIndex);
            if (firstQuote != -1) {
                // 找到第一个单引号后的第二个单引号
                int secondQuote = msg.indexOf("'", firstQuote + 1);
                if (secondQuote != -1) {
                    // 提取两个单引号之间的内容
                    String symbol = msg.substring(firstQuote + 1, secondQuote);
                    
                    // 将符号转换为大写形式
                    return convertSymbolToTokenName(symbol);
                }
            }
        }
        
        return "UNKNOWN_SYMBOL";
    }
    

    private String convertSymbolToTokenName(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            return "UNKNOWN_SYMBOL";
        }
        
        // 常见符号到名称的映射
        switch (symbol) {
            case ";": return "SEMI";
            case ",": return "COMMA";
            case "(": return "LPAREN";
            case ")": return "RPAREN";
            case "{": return "LBRACE";
            case "}": return "RBRACE";
            case "[": return "LBRACK";
            case "]": return "RBRACK";
            case "=": return "ASSIGN";
            case "+": return "PLUS";
            case "-": return "MINUS";
            case "*": return "STAR";  // 注意：这里应该是STAR，不是MULT
            case "/": return "DIV";
            case "%": return "MOD";
            case "<": return "LT";
            case ">": return "GT";
            case "==": return "EQ";
            case "!=": return "NEQ";
            case "<=": return "LE";
            case ">=": return "GE";
            case "&&": return "AND";
            case "||": return "OR";
            case "!": return "NOT";
            case "++": return "INC";
            case "--": return "DEC";
            case ".": return "DOT";
            case "->": return "ARROW";
            case "&": return "AMP";
            // 关键字映射
            case "int": return "INT";
            case "char": return "CHAR";
            case "struct": return "STRUCT";
            case "return": return "RETURN";
            case "if": return "IF";
            case "else": return "ELSE";
            case "while": return "WHILE";
            default:
                // 如果不是常见符号，尝试转换为大写形式
                return symbol.toUpperCase();
        }
    }
    
}