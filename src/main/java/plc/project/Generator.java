package plc.project;

import java.io.PrintWriter;
import java.math.BigDecimal;

public final class Generator implements Ast.Visitor<Void> {

    private final PrintWriter writer;
    private int indent = 0;

    public Generator(PrintWriter writer) {
        this.writer = writer;
    }

    private void print(Object... objects) {
        for (Object object : objects) {
            if (object instanceof Ast) {
                visit((Ast) object);
            } else {
                writer.write(object.toString());
            }
        }
    }

    private void newline(int indent) {
        writer.println();
        for (int i = 0; i < indent; i++) {
            writer.write("    ");
        }
    }

    @Override
    public Void visit(Ast.Source ast) {
        //1. generate class header
        print("public class Main {");
        newline(0);
        indent++;

        //2. generate fields (grouped together)
        if(!ast.getFields().isEmpty()) {
            for (Ast.Field field : ast.getFields()) {
                newline(indent);
                print(field);
            }
            newline(0); //new line after all fields
        }

        //3. generate main method
        newline(indent);
        print("public static void main(String[] args) {");
        newline(indent + 1);
        print("System.exit(new Main().main());");
        newline(indent);
        print("}");

        //4. generate methods (separated by empty line)
        for (Ast.Method method : ast.getMethods()) {
            newline(0);
            newline(indent);
            print(method);
        }

        //5. generate closing brace
        indent--;
        newline(0);
        newline(indent);
        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Field ast) {
         //non-const field
        if(!ast.getConstant()){
            print(ast.getVariable().getType().getJvmName(), " ", ast.getName());
            if (ast.getValue().isPresent()) {
                print(" = ");
                print(ast.getValue().get());
            }
        }
        //const field
        else{
            print("final ",  ast.getVariable().getType().getJvmName(), " ", ast.getName());

            if (ast.getValue().isPresent()) {
                print(" = ");
                print(ast.getValue().get());
            }
        }

        print(";");
        //newline(0); //do i need this??

        return null;
    }

    @Override
    public Void visit(Ast.Method ast) {
        //generate JVM type name then method name
        print(ast.getFunction().getReturnType().getJvmName(), " ", ast.getFunction().getJvmName());

        //generate comma list of method param in ().
        print("(");
        for (int i = 0; i < ast.getParameters().size(); i++) {
            if(i>0){
                print(", ");
            }
            Environment.Type paramType = ast.getFunction().getParameterTypes().get(i);
            print(paramType.getJvmName(), " ", ast.getParameters().get(i));
        }
        print(") {");

        //either closing brace or generate each statement
        if (!ast.getStatements().isEmpty()) {
            //generated on new line with inc indent
            indent++;
            for (Ast.Statement statement : ast.getStatements()) {
                newline(indent);
                print(statement);
            }
            indent--;
            newline(indent);
            print("}");
        }
        //DO I NEED THIS?!! if main is empty does it print main(){} or print main(){return 0;} ??
        else if (ast.getStatements().isEmpty() && ast.getFunction().getJvmName().equals("main")){
            newline(indent + 1);
            print("return 0;");
            newline(indent);
            print("}");
            indent--;
        }
        else{print("}");}

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        //generates expr
        print(ast.getExpression(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        //generate dec expr
        print(ast.getVariable().getType().getJvmName(), " ", ast.getName());

        //if val present
        if (ast.getValue().isPresent()) {
            print(" = ", ast.getValue().get());
        }
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        //generates a variable assignment expr
        print(ast.getReceiver(), " = ", ast.getValue(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        //generate condition
        print("if (", ast.getCondition(),") {");

        if(ast.getThenStatements().isEmpty()){
            print("}");
        }
        else {
            indent++;
            //generate each IF statement on new line
            for(Ast.Statement ifStatement : ast.getThenStatements()){
                newline(indent);
                print(ifStatement);
            }
            indent--;
            newline(indent);
            print("}");

            //check if there are any ELSE statements
            if (!ast.getElseStatements().isEmpty()) {
                print(" else {");
                indent++;

                //generate each one
                for (Ast.Statement elseStatement : ast.getElseStatements()) {
                    newline(indent);
                    print(elseStatement);
                }
                indent--;
                newline(indent);
                print("}");
            }
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.For ast) {
        //generates a for loop expr
        print("for ( ");

        //optional init statement
        if (ast.getInitialization() != null) {
            print(ast.getInitialization()); //trailing semicolons?
        }
        else{
            print(";");
        }
        //print(";");
        print(" ");

        //cond expr
        print(ast.getCondition());
        print(";"); //this needs to be here i think

        //optional increment statement
        if (ast.getIncrement() != null) {
            print(" ", ast.getIncrement());
        }
//        else{
//            print(";");
//        }

        //for some reason when i generate the increment, it prints a semicolon here

        print(" ) {");

        //generate each statement in for-loop on newline
        if(!ast.getStatements().isEmpty()){
            indent++;
            for (Ast.Statement statement : ast.getStatements()) {
                newline(indent);
                visit(statement);
            }
            indent--;
            newline(indent);
        }
        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Statement.While ast) {
        print("while (", ast.getCondition(),") {");

        if (!ast.getStatements().isEmpty()) {
            indent++;
            for (Ast.Statement statement : ast.getStatements()) {
                newline(indent);
                print(statement);
            }
            indent--;
            newline(indent);
            print("}");
        } else {
            print("}");
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        //generates a return expr
        print("return ", ast.getValue(),";");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {
        //Generates a literal expr
        if (ast.getLiteral() instanceof String) {
            print("\"" + ast.getLiteral() + "\"");
        }
        else if (ast.getLiteral() instanceof Character) {
            print("'" + ast.getLiteral() + "'");
        }
        else if (ast.getLiteral() instanceof BigDecimal) {
            print(((BigDecimal) ast.getLiteral()).toPlainString()); //?
        }
        else if (ast.getLiteral() == null) {
            print("null");
        }
        else {
            print(ast.getLiteral().toString());
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {
        //generates a group expr
        print("(", ast.getExpression(),")");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        //generate binary expr
        print(ast.getLeft(), " ", ast.getOperator(), " ", ast.getRight());
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Access ast) {
        if (ast.getReceiver().isPresent()) {
            print(ast.getReceiver().get(), ".");
        }

        //generates access expr
        print(ast.getVariable().getJvmName());
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
        if (ast.getReceiver().isPresent()) {
            print(ast.getReceiver().get(), ".");
        }

        //generates funct expr
        print(ast.getFunction().getJvmName(), "(");

        //generate argument expr
        for (int i = 0; i < ast.getArguments().size(); i++) {
            if(i>0){
                print(", ");
            }
            print(ast.getArguments().get(i));
        }
        print(")");

        return null;
    }
}
