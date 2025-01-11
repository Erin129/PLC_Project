package plc.project;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.math.BigInteger;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class Interpreter implements Ast.Visitor<Environment.PlcObject> {

    private Scope scope = new Scope(null);

    public Interpreter(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", 1, args -> {
            System.out.println(args.get(0).getValue());
            return Environment.NIL;
        });
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Environment.PlcObject visit(Ast.Source ast) {
        /*Evaluates globals followed by functions. Returns the result of calling the main/0 function.*/
        //first eval global fields
        for (Ast.Field field : ast.getFields()) {
            visit(field);
        }

        //def each funct
        for (Ast.Method method : ast.getMethods()) {
            visit(method);
        }

        //call the main function with arity 0, else throw runtime error
        Environment.Function funct = scope.lookupFunction("main", 0);
        if (funct == null) {
            throw new RuntimeException("Runtime error in source main/0 function is not defined.");
        }
        //invoke main/0
        return funct.invoke(List.of());
    }

    @Override
    public Environment.PlcObject visit(Ast.Field ast) {
        Environment.PlcObject fieldVal;

        //check if var has an initial value, or set to NIL
        if (ast.getValue().isPresent()) {
            fieldVal = visit(ast.getValue().get());
        } else {
            fieldVal = Environment.NIL;
        }

        //define the var in the current scope
        scope.defineVariable(ast.getName(), ast.getConstant(), fieldVal);

        //return NIL
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Method ast) {
        //make sure to def funct in curr scope
        scope.defineFunction(ast.getName(), ast.getParameters().size(), args -> {
            //scope for method
            Scope methodScope = new Scope(scope);

            //def the var within the method's scope
            for (int i = 0; i < ast.getParameters().size(); i++) {
                methodScope.defineVariable(ast.getParameters().get(i), false, args.get(i));
            }

            //copy the previous scope to restore later when done with this method call
            Scope prev = scope;
            scope = methodScope;

            Environment.PlcObject result = Environment.NIL;
            for (Ast.Statement statement : ast.getStatements()) {
                try {
                    visit(statement); //eval each statement
                } catch (Return returnValue) {
                    result = returnValue.value; ////return the val if present
                    break;
                }
            }
            scope = prev; //restore scope
            return result; //if no return val specified, return NIL
        });

        //visit(Ast.Method) should return NIL
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Expression ast) {
        //Evaluates the expression and returns NIL.
        visit(ast.getExpression());
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Declaration ast) {
        //Defines a local variable in the current scope, defaulting to NIL if no initial value is defined. Returns NIL.
        Environment.PlcObject value;
        if (ast.getValue().isPresent()) {
            value = visit(ast.getValue().get());
        } else {
            value = Environment.NIL;
        }

        scope.defineVariable(ast.getName(), false, value); //never constant

        //return NIL
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Assignment ast) {
        //first make sure it's of type Ast.Expression.Access
        if (!(ast.getReceiver() instanceof Ast.Expression.Access)) {
            throw new RuntimeException("receiver is not of type Ast.Expression.Access :(");
        }

        Environment.PlcObject assignmentVal = visit(ast.getValue());
        Ast.Expression.Access recv = (Ast.Expression.Access) ast.getReceiver();

        //check if there is a receiver
        if (recv.getReceiver().isPresent()) {
            //if so, evaluate it
            Environment.PlcObject receiver = visit(recv.getReceiver().get());
            Environment.Variable field = receiver.getField(recv.getName());

            //Assignments to a non-NIL, constant field will cause the evaluation to fail!
            if (!field.getValue().equals(Environment.NIL) && field.getConstant()) {
                throw new RuntimeException("error reassigning const var in Ast.Statement.Assignment");
            }

            field.setValue(assignmentVal); //set associated field
        }

        else {
            //else lookup and set a variable in the current scope.
            Environment.Variable variable = scope.lookupVariable(recv.getName());

            //Assignments to a non-NIL, constant field will cause the evaluation to fail!
            if (!variable.getValue().equals(Environment.NIL) && variable.getConstant()) {
                throw new RuntimeException("error reassigning const var in Ast.Statement.Assignment");
            }

            variable.setValue(assignmentVal); //set associated var
        }

        return Environment.NIL; //return NIL
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.If ast) {

        Environment.PlcObject condition = visit(ast.getCondition());
        //Boolean conditionType = requireType(Boolean.class, condition);

        //make sure condition is a Boolean type,
        if (!(condition.getValue() instanceof Boolean)) {
            throw new RuntimeException("The condition does NOT evaluate to a Boolean");
        }

        //determine whether condition value is true or false boolean
        Boolean conditionValue = (Boolean)condition.getValue();

        //set new scope and save prev one
        Scope ifScope = new Scope(scope);
        Scope prevScope = scope;
        scope = ifScope;

        List<Ast.Statement> ifStatements;
        if(conditionValue){ //if condition==TRUE
            ifStatements = ast.getThenStatements(); //get then statements
        }
        else{ //if FALSE
            ifStatements = ast.getElseStatements(); //get else statements
        }

        for (Ast.Statement statement : ifStatements) {
            visit(statement); //evaluate each statement
        }

        scope = prevScope; //restore scope
        return Environment.NIL; //return NIL
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.For ast) {
        visit(ast.getInitialization()); //eval init statement b4 loop

        Scope forScope = new Scope(scope);
        Scope prevScope = scope;
        scope = forScope;


        while (true) {
            Environment.PlcObject condition = visit(ast.getCondition());
            if (!(condition.getValue() instanceof Boolean)) {
                throw new RuntimeException("The condition does not evaluate to a Boolean");
            }

            //determine whether condition value is true or false boolean
            Boolean conditionValue = (Boolean)condition.getValue();
            if (!conditionValue) {
                break;
            }

            //if condition==TRUE
            for (Ast.Statement statement : ast.getStatements()) {
                visit(statement); //evaluate each statement
            }

            visit(ast.getIncrement()); //evaluate the increment statement (i++)
        }

        scope = prevScope; //restore scope
        return Environment.NIL; //return NIL
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.While ast) {
        Scope whileScope = new Scope(scope);
        Scope prevScope = scope;
        scope = whileScope;

        while (true) {
            Environment.PlcObject condition = visit(ast.getCondition()); //re-evaluate condition

            if (!(condition.getValue() instanceof Boolean)) {
                throw new RuntimeException("The condition does NOT evaluate to a Boolean");
            }

            //determine whether condition value is true or false boolean
            Boolean conditionValue = (Boolean)condition.getValue();
            if (!conditionValue) {
                break;
            }

            //if TRUE, eval statements
            for (Ast.Statement statement : ast.getStatements()) {
                visit(statement);
            }
        }
        scope = prevScope; //restore scope
        return Environment.NIL;  //return NIL
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Return ast) {
        //Evaluates the value and throws it inside in a Return exception
        Environment.PlcObject val = visit(ast.getValue());
        throw new Return(val);
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Literal ast) {
        // for our prog, null should be changes to our own representation of NIL
        if(ast.getLiteral() == null) {
            return Environment.NIL;
        }
        // for everything else, return literal value as a plcObject using create
        return Environment.create(ast.getLiteral());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Group ast) {
        //Evaluates the contained expression, returning its value.
        return visit(ast.getExpression());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Binary ast) {
        // get the left and right objects from the binary expression
        // only define the left because for short circuit on "OR"
        // to work, it needs to ignore the right side
        Environment.PlcObject left = visit(ast.getLeft());

        // perform a check for "OR"
        if (ast.getOperator().equals("||")) {
            // same as above
            requireType(Boolean.class, left);
            if ((Boolean) left.getValue()) {
                // if left exp is true, don't need to check right
                return Environment.create(true);
            }
            // now define the right expression for this case
            Environment.PlcObject right = visit(ast.getRight());
            requireType(Boolean.class, right);
            return Environment.create((Boolean) right.getValue());
        }

        // now can create right expression operator

        // continue with all other operations
        Environment.PlcObject right = visit(ast.getRight());
        if (ast.getOperator().equals("&&")) {
            // check that left expression is true
            requireType(Boolean.class, left);
            // if left is false, stop here
            if (!(Boolean) left.getValue()) {
                return Environment.create(false);
            }
            // check that right exp is tru
            requireType(Boolean.class, right);
            // if it is, the environment object returned is true
            // if its false, returns false
            return Environment.create((Boolean) right.getValue());
        }
        else if (ast.getOperator().equals("<")) {
            // ensure left exp is Comparable
            requireType(Comparable.class, left);
            // ensure right is same type as left
            requireType(left.getValue().getClass(), right);
            // perform the comparison
            int comparison = ((Comparable) left.getValue()).compareTo(right.getValue());
            return Environment.create(comparison < 0);
        }
        else if (ast.getOperator().equals("<=")) {
            // same as above, but if <=, comparison is <= 0
            requireType(Comparable.class, left);
            requireType(left.getValue().getClass(), right);
            int comparison = ((Comparable) left.getValue()).compareTo(right.getValue());
            return Environment.create(comparison <= 0);
        }
        else if (ast.getOperator().equals(">")) {
            // same steps, but check if comparison > 0
            requireType(Comparable.class, left);
            requireType(left.getValue().getClass(), right);
            int comparison = ((Comparable) left.getValue()).compareTo(right.getValue());
            return Environment.create(comparison > 0);
        }
        else if (ast.getOperator().equals(">=")) {
            // same as above (there is def a more efficient way to do this)
            // but check if comparison <= 0
            requireType(Comparable.class, left);
            requireType(left.getValue().getClass(), right);
            int comparison = ((Comparable) left.getValue()).compareTo(right.getValue());
            return Environment.create(comparison > 0);
        }
        else if (ast.getOperator().equals("==")) {
            // evaluate if the left and right expressions equal eachother
            boolean equal = Objects.equals(left.getValue(), right.getValue());
            return Environment.create(equal);
        }
        else if (ast.getOperator().equals("!=")) {
            // same as before, but want them to not equal eachother
            boolean equal = Objects.equals(left.getValue(), right.getValue());
            return Environment.create(!equal);
        }
        else if (ast.getOperator().equals("+")) {
            // check if either side is a string
            if (left.getValue() instanceof String || right.getValue() instanceof String) {
                // perform string concatenation
                return Environment.create(requireType(String.class, left) + requireType(String.class, right));
            }
            // check if left is BigInt, right must be too
            else if (left.getValue() instanceof BigInteger && right.getValue() instanceof BigInteger) {
                // return result of integer addition
                return Environment.create(requireType(BigInteger.class, left).add(requireType(BigInteger.class, right)));
            }
            // if both exp are BigDec
            else if (left.getValue() instanceof BigDecimal && right.getValue() instanceof BigDecimal) {
                // return result of decimal addition
                return Environment.create(requireType(BigDecimal.class, left).add(requireType(BigDecimal.class, right)));
            }
            else {
                throw new RuntimeException("Trying to add incompatible types.");
            }
        }
        else if (ast.getOperator().equals("-")) {
            // left and right are both BigInt
            if (left.getValue() instanceof BigInteger && right.getValue() instanceof BigInteger) {
                // create the left and right BigInts so they're the correct type before subtracting
                BigInteger leftInt = (BigInteger) left.getValue();
                BigInteger rightInt = (BigInteger) right.getValue();
                return Environment.create(leftInt.subtract(rightInt));
            }
            // left and right are both BigDec
            if (left.getValue() instanceof BigDecimal && right.getValue() instanceof BigDecimal) {
                // create the left and right BigInts so they're the correct type before subtracting
                BigDecimal leftDec = (BigDecimal) left.getValue();
                BigDecimal rightDec = (BigDecimal) right.getValue();
                return Environment.create(leftDec.subtract(rightDec));
            }
            // or something is wrong
            throw new RuntimeException("Trying to subtract incompatible types.");
        }
        else if (ast.getOperator().equals("*")) {
            // same as subtraction, but use multiplication
            // left and right are both BigInt
            if (left.getValue() instanceof BigInteger && right.getValue() instanceof BigInteger) {
                // create the left and right BigInts so they're the correct type before subtracting
                BigInteger leftInt = (BigInteger) left.getValue();
                BigInteger rightInt = (BigInteger) right.getValue();
                return Environment.create(leftInt.multiply(rightInt));
            }
            // left and right are both BigDec
            if (left.getValue() instanceof BigDecimal && right.getValue() instanceof BigDecimal) {
                // create the left and right BigInts so they're the correct type before subtracting
                BigDecimal leftDec = (BigDecimal) left.getValue();
                BigDecimal rightDec = (BigDecimal) right.getValue();
                return Environment.create(leftDec.multiply(rightDec));
            }
            // or something is wrong
            throw new RuntimeException("Trying to multiply incompatible types.");
        }
        else if (ast.getOperator().equals("/")) {
            // veery similar to subtraction and multiply, but check that right (denominator) doesnt equal 0
            // left and right are both BigInt
            if (left.getValue() instanceof BigInteger && right.getValue() instanceof BigInteger) {
                // create the left and right BigInts so they're the correct type before subtracting
                BigInteger leftInt = (BigInteger) left.getValue();
                BigInteger rightInt = (BigInteger) right.getValue();
                // check for division by 0
                if (rightInt.equals(BigInteger.ZERO)) {
                    throw new RuntimeException("Trying to divide by zero.");
                }
                return Environment.create(leftInt.divide(rightInt));
            }
            // left and right are both BigDec
            if (left.getValue() instanceof BigDecimal && right.getValue() instanceof BigDecimal) {
                // create the left and right BigInts so they're the correct type before subtracting
                BigDecimal leftDec = (BigDecimal) left.getValue();
                BigDecimal rightDec = (BigDecimal) right.getValue();

                if (rightDec.equals(BigDecimal.ZERO)) {
                    throw new RuntimeException("Trying to divide by zero.");
                }
                return Environment.create(leftDec.divide(rightDec, RoundingMode.HALF_EVEN));
            }
            // or something is wrong
            throw new RuntimeException("Trying to divide incompatible types.");
        }
        else{
            throw new RuntimeException("Cannot identify operator.");
        }
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Access ast) {
        // check if has receiver
        if (ast.getReceiver().isPresent()) {
            // evaluate it
            Environment.PlcObject receiver = visit(ast.getReceiver().get());
            // return appropriate field
            Environment.PlcObject field = receiver.getField(ast.getName()).getValue();
            return field;
        }
        else {
            // otherwise return value of variable in current scope
            Environment.PlcObject variable = scope.lookupVariable(ast.getName()).getValue();
            return variable;

        }
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Function ast) {
        // first get the arguments - evaluate them
        List<Environment.PlcObject> arguments = new ArrayList<>();
        for (Ast.Expression argument : ast.getArguments()) {
            arguments.add(visit(argument));
        }

        // check if the expression has a receiver
        if (ast.getReceiver().isPresent()) {
            // there is a receiver like class.function(argument)
            // evaluate it
            Environment.PlcObject receiver = visit(ast.getReceiver().get());
            // return result of calling the method
            try {
                return receiver.callMethod(ast.getName(), arguments);
            } catch (RuntimeException e) {
                throw new RuntimeException("Method " + ast.getName() + "/" + arguments.size() + " not found in receiver", e);
            }
        }
        else {
            //return value of invoking function with arguments
            Environment.Function function = scope.lookupFunction(ast.getName(), arguments.size());
            return function.invoke(arguments);
        }
    }

    /**
     * Helper function to ensure an object is of the appropriate type.
     */
    private static <T> T requireType(Class<T> type, Environment.PlcObject object) {
        if (type.isInstance(object.getValue())) {
            return type.cast(object.getValue());
        } else {
            throw new RuntimeException("Expected type " + type.getName() + ", received " + object.getValue().getClass().getName() + ".");
        }
    }

    /**
     * Exception class for returning values.
     */
    private static class Return extends RuntimeException {

        private final Environment.PlcObject value;

        private Return(Environment.PlcObject value) {
            this.value = value;
        }

    }

}
