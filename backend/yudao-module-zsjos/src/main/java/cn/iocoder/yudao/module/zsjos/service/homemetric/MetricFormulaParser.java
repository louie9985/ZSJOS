package cn.iocoder.yudao.module.zsjos.service.homemetric;

/** Parses only decimal literals, operand names, parentheses and four arithmetic operators. */
public final class MetricFormulaParser {
    private final String text;
    private int position;
    private int nesting;

    private MetricFormulaParser(String text) { this.text = text; }

    public static MetricFormula.Node parse(String text) {
        if (text == null || text.isBlank() || text.length() > 2048)
            throw new IllegalArgumentException("公式为空或过长");
        MetricFormulaParser parser = new MetricFormulaParser(text);
        MetricFormula.Node result = parser.expression();
        parser.whitespace();
        if (parser.position != text.length()) throw parser.invalid();
        MetricFormula.variables(result);
        return result;
    }

    private MetricFormula.Node expression() {
        MetricFormula.Node result = product();
        while (true) {
            if (take('+')) result = binary(MetricFormula.Operator.ADD, result, product());
            else if (take('-')) result = binary(MetricFormula.Operator.SUBTRACT, result, product());
            else return result;
        }
    }

    private MetricFormula.Node product() {
        MetricFormula.Node result = atom();
        while (true) {
            if (take('*')) result = binary(MetricFormula.Operator.MULTIPLY, result, atom());
            else if (take('/')) result = binary(MetricFormula.Operator.DIVIDE, result, atom());
            else return result;
        }
    }

    private MetricFormula.Node atom() {
        if (++nesting > 16) throw invalid();
        try {
            if (take('(')) {
                MetricFormula.Node value = expression();
                if (!take(')')) throw invalid();
                return value;
            }
            // Multiplication by -1 preserves the operand's unit, unlike subtracting it from scalar zero.
            if (take('-')) return binary(MetricFormula.Operator.MULTIPLY,
                    new MetricFormula.Node(MetricFormula.Operator.CONSTANT, "-1", null, null), atom());
            if (take('+')) return atom();
            whitespace();
            int start = position;
            if (position < text.length() && letter(text.charAt(position))) {
                position++;
                while (position < text.length()) {
                    char c = text.charAt(position);
                    if (!(letter(c) || digit(c) || c == '_')) break;
                    position++;
                }
                return new MetricFormula.Node(MetricFormula.Operator.VARIABLE, text.substring(start, position), null, null);
            }
            while (position < text.length() && digit(text.charAt(position))) position++;
            if (position == start) throw invalid();
            if (position < text.length() && text.charAt(position) == '.') {
                position++;
                int decimals = position;
                while (position < text.length() && digit(text.charAt(position))) position++;
                if (position == decimals) throw invalid();
            }
            return new MetricFormula.Node(MetricFormula.Operator.CONSTANT, text.substring(start, position), null, null);
        } finally { nesting--; }
    }

    private boolean take(char expected) {
        whitespace();
        if (position >= text.length() || text.charAt(position) != expected) return false;
        position++;
        return true;
    }
    private void whitespace() {
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) position++;
    }
    private static boolean letter(char c) { return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z'; }
    private static boolean digit(char c) { return c >= '0' && c <= '9'; }
    private IllegalArgumentException invalid() { return new IllegalArgumentException("公式在第 " + (position + 1) + " 个字符附近无效"); }
    private MetricFormula.Node binary(MetricFormula.Operator operator, MetricFormula.Node left, MetricFormula.Node right) {
        return new MetricFormula.Node(operator, null, left, right);
    }
}
