package plc.project;
import java.math.BigDecimal;
import java.math.BigInteger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * See the specification for information about what the different visit
 * methods should do.
 */
public final class Analyzer implements Ast.Visitor<Void> {
    public Scope scope;
    private Ast.Method method;
    private Ast.Expression expectedReturnType;

    public Analyzer(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL);
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Void visit(Ast.Source ast) {
        //visit fields
        for (Ast.Field field : ast.getFields()) {
            visit(field);
        }

        //visit each method and check for runtime errors
        boolean foundMain = false;
        for (Ast.Method method : ast.getMethods()) {
            //check if main/0 exists
            if (method.getName().equals("main") && method.getParameters().isEmpty()) {
                foundMain = true;
                //and check if return type Integer

                // potential error here
                // can you call .equals on something that is null? -> check if present first
                if (method.getReturnTypeName().isEmpty() || !method.getReturnTypeName().get().equals("Integer")) {
                    throw new RuntimeException("main/0 does not have Integer return type");
                }
            }
            visit(method);
        }

        //check if main/0 funct was found
        if (!foundMain) {
            throw new RuntimeException("main/0 method not found");
        }

        //return null
        return null;
    }

    @Override
    public Void visit(Ast.Field ast) {
        //get field variable type
        Environment.Type type = Environment.getType(ast.getTypeName());

        //The value of the field must be visited before the variable is def!

        //check if a value is present
        if (ast.getValue().isPresent()) {
            //visit field val
            visit(ast.getValue().get());

            //store the expression type
            Environment.Type value_Type = ast.getValue().get().getType();

            //next check if value type is assignable to the field type (if not throw error)
            requireAssignable(type, value_Type);
        }

        //else if field value not present but was def as constant
        else if (ast.getConstant()) {
            throw new RuntimeException("constant field with no initial value");
        }

        //def variable in the curr scope with name, jvmName, field type, default value NIL, and boolean const
        Environment.Variable variable = scope.defineVariable(ast.getName(), ast.getName(), type, ast.getConstant(), Environment.NIL);

        //also set the variable in the AST
        ast.setVariable(variable);

        return null;
    }

    @Override
    public Void visit(Ast.Method ast) {
        //def param types according to AST
        List<Environment.Type> paramTypes = new ArrayList<>();
        for (String param : ast.getParameterTypeNames()) {
            paramTypes.add(Environment.getType(param));
        }

        // initialize the return type as nil
        Environment.Type returnType = Environment.Type.NIL;
//        Boolean returnPresent = ast.getReturnTypeName().isPresent();
//
//        if(returnPresent) {
//            // check that the return type isnt null
//            if (ast.getReturnTypeName().get() != null) {
//                //get return type if present, or NIL as default
//                returnType = Environment.getType(ast.getReturnTypeName().get());
//            }
//        }
        if (ast.getReturnTypeName().isPresent()) {
            String returnTypeName = ast.getReturnTypeName().get();
            returnType = Environment.getType(returnTypeName);
            if (returnType == null) {
                throw new RuntimeException("Return type null");
            }
        }

        //def funct in scope and set in AST
        Environment.Function newFunct = new Environment.Function(ast.getName(), ast.getName(), paramTypes, returnType, args -> Environment.NIL);
        scope.defineFunction(ast.getName(), ast.getName(), paramTypes, returnType, args -> Environment.NIL);
        ast.setFunction(newFunct);

        //save expected return type
        //should fix the unit type bug we were having!
        Ast.Expression.Literal literal = new Ast.Expression.Literal(returnType);
        literal.setType(returnType);
        this.expectedReturnType = literal;
        //System.out.println("Expected return type: " + this.expectedReturnType.getType());

        //visit all statements inside new scope containing variables for each parameter:
        Scope method_Scope = new Scope(scope);
        for (int i = 0; i < ast.getParameters().size(); i++) {
            String name = ast.getParameters().get(i);
            Environment.Type type = paramTypes.get(i);
            method_Scope.defineVariable(name, name, type, false, Environment.NIL);
        }

        scope = method_Scope; //do i need this?
        //visit each statement in method
        for (Ast.Statement statement : ast.getStatements()) {
            visit(statement);
        }

        //restore prev scope!
        scope = scope.getParent();

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        Ast.Expression expr = ast.getExpression();

        //check if type Ast.Expression.Function
        if (!(expr instanceof Ast.Expression.Function)) {
            //if not, throw exception
            throw new RuntimeException("not of type function expression");
        }

        //If the expression is valid, visit and return null
        visit(expr);
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        //determine variable type
        Environment.Type type;
        if (ast.getTypeName().isPresent()) {
            //var type = one registered in the Environment with name from AST
            type = Environment.getType(ast.getTypeName().get());
        }
        else if (ast.getValue().isPresent()) { //else type=value
            visit(ast.getValue().get());
            type = ast.getValue().get().getType();
        }
        else { //if neither present -> error
            throw new RuntimeException("error determining variable type");
        }

        //if the value is present, make sure it's assignable
        if (ast.getValue().isPresent()) {
            visit(ast.getValue().get());
            requireAssignable(type, ast.getValue().get().getType());
        }

        //def variable in curr scope and set in AST
        Environment.Variable variable = scope.defineVariable(ast.getName(), ast.getName(), type, false, Environment.NIL);
        ast.setVariable(variable);

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        //visit receiver and value
        visit(ast.getReceiver());
        visit(ast.getValue());

        //make sure recv is access expr
        if (!(ast.getReceiver() instanceof Ast.Expression.Access)) {
            throw new RuntimeException("recv is not an access expression");
        }

        //declare recv and variable
        Ast.Expression.Access recv = (Ast.Expression.Access) ast.getReceiver();
        Environment.Variable variable;

        //for cases like object.field with parent:
        if (recv.getReceiver().isPresent()) {
            Ast.Expression parentExpr = recv.getReceiver().get();
            visit(parentExpr);

            // Get the parent obj type and set variable
            Environment.Type parentType = parentExpr.getType();
            variable = parentType.getField(recv.getName());

            if (variable == null) {
                throw new RuntimeException("The field is not defined for parent obj");
            }
        }
        //else for cases w/o a parent obj
        else {
            variable = scope.lookupVariable(recv.getName());
        }

        //make sure val is assignable to recv
        requireAssignable(variable.getType(), ast.getValue().getType());

        //throw error if assign to const field
        if (variable.getConstant()) {
            throw new RuntimeException("Cannot assign to a constant field after declaration");
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        //make sure condition is type Boolean
        visit(ast.getCondition());
        if (ast.getCondition().getType() != Environment.Type.BOOLEAN) {
            throw new RuntimeException("Error: condition is not a Boolean");
        }

        //make sure thenStatements is non-empty
        if (ast.getThenStatements().isEmpty()) {
            throw new RuntimeException("Error: thenStatements list is empty");
        }

        //visit then and else statements (if any) inside new scope for each one
        Scope thenScope = new Scope(scope); // Create a new scope for then statements
        scope = thenScope;
        for (Ast.Statement thenStatement : ast.getThenStatements()) {
            //scope = new Scope(scope);
            visit(thenStatement);
        }
        scope = scope.getParent();

        if (!ast.getElseStatements().isEmpty()) {
            Scope elseScope = new Scope(scope);
            scope = elseScope;
            for (Ast.Statement elseStatement : ast.getElseStatements()) {
                scope = new Scope(scope);
                visit(elseStatement);
            }
            scope = scope.getParent(); //restore prev scope
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.For ast) {
        // 'FOR' '(' (identifier '=' expression)? ';' expression ';' (identifier '=' expression)? ')' statement*
        // first, perform all checks to determine if runtime error must be thrown

        // check if initializer exists and is a comparable type
        Ast.Statement initializer = ast.getInitialization();
        if (initializer != null) {
            visit(initializer);
            if (initializer instanceof Ast.Statement.Declaration) {
                Ast.Statement.Declaration initDec = (Ast.Statement.Declaration) initializer;
                requireAssignable(Environment.Type.COMPARABLE, initDec.getVariable().getType());
            }
        }

        //make sure condition is type boolean
        //side note: ***remember to visit the variables BEFORE accessing them***
        Ast.Expression cond = ast.getCondition();
        visit(cond);
        if(cond.getType() != Environment.Type.BOOLEAN) {
            throw new RuntimeException("condition is not a Boolean.");
        }

        //check if expr in the increment is same type as init identifier
        Ast.Statement increment = ast.getIncrement();
        if (increment != null) {
            visit(increment);
            if (increment instanceof Ast.Statement.Assignment && initializer instanceof Ast.Statement.Declaration) {
                Ast.Statement.Assignment incrAssign = (Ast.Statement.Assignment) increment;
                Ast.Statement.Declaration initDec = (Ast.Statement.Declaration) initializer;
                //check types
                requireAssignable(initDec.getVariable().getType(), incrAssign.getValue().getType());
            }
        }

        // check if list statement is empty
        if(ast.getStatements().isEmpty()) {
            throw new RuntimeException("Statement list is empty.");
        }

        //After visiting the condition, visit each statement in the body in new scope
        scope = new Scope(scope);
        // visit statements
        for (Ast.Statement statement : ast.getStatements()) {
            visit(statement);
        }
        scope = scope.getParent(); //restore scope

        return null;
    }

    @Override
    public Void visit(Ast.Statement.While ast) {
        // validate/capture the condition
        Ast.Expression condition = ast.getCondition();
        visit(condition);
        // check the condition statement is of type boolean
        if (condition.getType() != Environment.Type.BOOLEAN) {
            throw new RuntimeException("Error: Expected condition to be Boolean.");
        }
        else{
            //in a new scope
            scope = new Scope(scope);
            // visit all the statements
            for (Ast.Statement statement : ast.getStatements()) {
                visit(statement);
            }
            scope = scope.getParent(); //restore scope
            return null;
        }
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        visit(ast.getValue());

        //make sure return value is assignable to the expected return type
        requireAssignable(expectedReturnType.getType(), ast.getValue().getType());

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {
        // first get the literal so you can use set type function
        Object literal = ast.getLiteral();

        // now set the type based on what it is
        // no additional behavior for the first for
        if (literal == null) {
            ast.setType(Environment.Type.NIL);
            return null;
        } else if (literal instanceof Boolean){
            ast.setType(Environment.Type.BOOLEAN);
            return null;
        } else if (literal instanceof Character) {
            ast.setType(Environment.Type.CHARACTER);
            return null;
        } else if (literal instanceof String) {
            ast.setType(Environment.Type.STRING);
            return null;
        } else if (literal instanceof BigInteger) {
            // check if value is out of range of a 32-bit signed int
            // create values for the bounds
            BigInteger maxInt = BigInteger.valueOf(Integer.MAX_VALUE);
            BigInteger minInt = BigInteger.valueOf(Integer.MIN_VALUE);

            // must make object into BigInt for comparison
            BigInteger BigInt = (BigInteger) literal;
            if (BigInt.compareTo(maxInt) <= 0 && BigInt.compareTo(minInt) >= 0) {
                //System.out.println("does this hit?");
                ast.setType(Environment.Type.INTEGER);
                //System.out.println("Literal type set to: " + ast.getType());
            }
            else {
                // throw runtime error
                throw new RuntimeException("Runtime Error: Integer value not within supported range");
            }
            return null;
        } else if (literal instanceof BigDecimal) {
            // create values for the bounds
            BigDecimal maxDouble = new BigDecimal(Double.MAX_VALUE);
            BigDecimal minDouble = new BigDecimal(-Double.MAX_VALUE);

            // "cast" it to a BigDec for comparison
            BigDecimal BigDec = (BigDecimal) literal;

            // cannot use <> on BigDec so use compareTo()
            if (BigDec.compareTo(minDouble) > 0 && BigDec.compareTo(maxDouble) < 0) {
                ast.setType(Environment.Type.DECIMAL);
            }
            else {
                throw new RuntimeException("Runtime Error: Decimal value not within supported range");
            }
            return null;
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {
        // check that contained expression is binary
        if (ast.getExpression() instanceof Ast.Expression.Binary) {
            // visit the binary expression
            visit(ast.getExpression());
            // set the type to be whatever is inside the binary expression
            ast.setType(ast.getExpression().getType());
            return null;

        }
        else {
            throw new RuntimeException("Error: Expected Binary Expression inside group");
        }
    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        // first get/evaluate the left and right expressions, so can check type
        Ast.Expression left = ast.getLeft();
        Ast.Expression right = ast.getRight();
        visit(left);
        visit(right);

        // throw a runtime error if either of the sides is type null
        try {
            // attempt to get the type
            Environment.Type type = left.getType();
        }
        catch (IllegalStateException e) {
            // the type must have been null
            throw new RuntimeException();
        }
        try {
            // attempt to get the type
            Environment.Type type = right.getType();
        }
        catch (IllegalStateException e) {
            // the type must have been null
            throw new RuntimeException();
        }


        // now validate types based on operator and freely use the getType() function
        if (ast.getOperator().equals("&&") || ast.getOperator().equals("||")) {
            // both operands must be Boolean
            if (left.getType() == Environment.Type.BOOLEAN && right.getType() == Environment.Type.BOOLEAN) {
                // set the result type to Boolean
                ast.setType(Environment.Type.BOOLEAN);
                return null;
            }
            else {
                throw new RuntimeException("Runtime Error: Both operands are not Booleans");
            }
        } else if (ast.getOperator().equals("<") || ast.getOperator().equals("<=") || ast.getOperator().equals(">") ||
                ast.getOperator().equals(">=") || ast.getOperator().equals("==") || ast.getOperator().equals("!=")) {
            // comparison using </<=/>/>=/==/!=
            // both operands must be comparable
            // comparable types are integer, decimal, character, and string
            requireAssignable(Environment.Type.COMPARABLE, left.getType());
            requireAssignable(Environment.Type.COMPARABLE, right.getType());

            // check they're the same type
            requireAssignable(left.getType(), right.getType());

            // set the type
            ast.setType(Environment.Type.BOOLEAN);
            return null;
        } else if (ast.getOperator().equals("+")) {
            // check if either side is a string
            if (left.getType() == Environment.Type.STRING || right.getType() == Environment.Type.STRING) {
                ast.setType(Environment.Type.STRING);
            }
            // LHS must be an Integer/Decimal
            else {
                if (left.getType() == Environment.Type.INTEGER || left.getType() == Environment.Type.DECIMAL) {
                    // ensure they're same type
                    requireAssignable(left.getType(), right.getType());
                    // set type
                    ast.setType(left.getType());
                    return null;
                }
                else {
                    throw new RuntimeException("Error: Left hand side not instance of type Integer or Decimal");
                }
            }
        } else if (ast.getOperator().equals("-") || ast.getOperator().equals("*") || ast.getOperator().equals("/")) {
            // same as for + with int or dec
            if (left.getType() == Environment.Type.INTEGER || left.getType() == Environment.Type.DECIMAL) {
                // ensure they're same type
                requireAssignable(left.getType(), right.getType());
                ast.setType(left.getType());
                return null;
            }
            else {
                throw new RuntimeException("Error: Left hand side not instance of type Integer or Decimal");
            }
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Access ast) {
        // check if there is a receiver
        if (ast.getReceiver().isPresent()) {
            // access the optional object
            Ast.Expression receiver = ast.getReceiver().get();
            // visit it
            visit(receiver);
            // set the variable
            ast.setVariable(receiver.getType().getField(ast.getName()));
        }
        else {
            // is not field
            // simply set the variable
            ast.setVariable(scope.lookupVariable(ast.getName()));
        }

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
        // check if it's a method or a function
        if (ast.getReceiver().isPresent()) {
            // receiver present so its method
            // use .get() to retrieve optional object
            Ast.Expression receiver = ast.getReceiver().get();
            visit(receiver);

            // now check that arguments and parameters match types
            // declare the envrionment.function object to access parameter types

            Environment.Function function = receiver.getType().getFunction(ast.getName(), ast.getArguments().size());
            // get the arguments from the ast
            List<Ast.Expression> arguments = ast.getArguments();
            //get what they're supposed to be
            List<Environment.Type> paramTypes = function.getParameterTypes();

            // iterate through and compare
            // first argument index at 1
            for (int i = 1; i < arguments.size(); i++) {
                visit(arguments.get(i));
                requireAssignable(paramTypes.get(i), arguments.get(i).getType());
            }
            // set the function of the expression
            ast.setFunction(function);
        }
        else {
            // receiver not present; must be function
            // declare the environment.function object
            Environment.Function function = scope.lookupFunction(ast.getName(), ast.getArguments().size());

            List<Ast.Expression> arguments = ast.getArguments();
            List<Environment.Type> paramTypes = function.getParameterTypes();

            // now, first object counts, start at 0
            for (int i = 0; i < arguments.size(); i++) {
                visit(arguments.get(i));
                requireAssignable(paramTypes.get(i), arguments.get(i).getType());
            }
            // set function
            ast.setFunction(function);
        }
        return null;
    }

    public static void requireAssignable(Environment.Type target, Environment.Type type) {
        // when the two types are the same
        if (target.getName().equals(type.getName())) {
            return;
        }

        // when the target type is any
        if (target.getName().equals("Any")) {
            return;
        }
        // when the target type is comparable
        if (target.getName().equals("Comparable")) {
            // check that type is integer, decimal, character or string
            if (type.getName().equals("Integer")) {
                return;
            } else if (type.getName().equals("Decimal")){
                return;
            } else if (type.getName().equals("Character")) {
                return;
            } else if (type.getName().equals("String")) {
                return;
            }
        }

        // if reaches this code, throw runtime error
        throw new RuntimeException("Error: Cannot Assign Types");
    }

}
