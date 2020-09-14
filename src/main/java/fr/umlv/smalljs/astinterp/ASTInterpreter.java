package fr.umlv.smalljs.astinterp;

import static fr.umlv.smalljs.rt.JSObject.UNDEFINED;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.stream.Collectors;

import fr.umlv.smalljs.ast.Expr;
import fr.umlv.smalljs.ast.Expr.Block;
import fr.umlv.smalljs.ast.Expr.FieldAccess;
import fr.umlv.smalljs.ast.Expr.FieldAssignment;
import fr.umlv.smalljs.ast.Expr.Fun;
import fr.umlv.smalljs.ast.Expr.FunCall;
import fr.umlv.smalljs.ast.Expr.If;
import fr.umlv.smalljs.ast.Expr.Literal;
import fr.umlv.smalljs.ast.Expr.LocalVarAccess;
import fr.umlv.smalljs.ast.Expr.LocalVarAssignment;
import fr.umlv.smalljs.ast.Expr.MethodCall;
import fr.umlv.smalljs.ast.Expr.New;
import fr.umlv.smalljs.ast.Expr.Return;
import fr.umlv.smalljs.ast.Script;
import fr.umlv.smalljs.ast.Visitor;
import fr.umlv.smalljs.rt.Failure;
import fr.umlv.smalljs.rt.JSObject;

public class ASTInterpreter {
    private static <T> T as(Object value, Class<T> type, Expr failedExpr) {
        try {
            return type.cast(value);
        } catch(@SuppressWarnings("unused") ClassCastException e) {
            throw new Failure("at line " + failedExpr.lineNumber() + ", type error " + value + " is not a " + type.getSimpleName());
        }
    }

    static Object visit(Expr expr, JSObject env) {
        return VISITOR.visit(expr, env);
    }

    private static final Visitor<JSObject, Object> VISITOR =
            new Visitor<JSObject, Object>()
                    .when(Block.class, (block, env) -> {
                        for(var instruction : block.instrs()) {
                            visit(instruction, env);
                        }
                        return UNDEFINED;
                    })
                    .when(Literal.class, (literal, env) -> literal.value())
                    .when(FunCall.class, (funCall, env) -> {
                        var value = visit(funCall.qualifier(), env);
                        var function = as(value, JSObject.class, funCall);
                        var arguments = funCall.args().stream().map(instr -> visit(instr, env)).toArray();
                        return function.invoke(UNDEFINED, arguments);
                    })
                    .when(LocalVarAccess.class, (localVarAccess, env) -> env.lookup(localVarAccess.name()))
                    .when(LocalVarAssignment.class, (localVarAssignment, env) -> {
                        var lookup = env.lookup(localVarAssignment.name());
                        if(localVarAssignment.declaration() && lookup != UNDEFINED) { // var localVar = x
                            throw new Failure("variable " + localVarAssignment.name() + " already defined");
                        }
                        if(!localVarAssignment.declaration() && lookup == UNDEFINED) { // localVar = x
                            throw new Failure("variable " + localVarAssignment.name() + " not defined");
                        }
                        env.register(localVarAssignment.name(), visit(localVarAssignment.expr(), env));
                        return UNDEFINED;
                    })
                    .when(Fun.class, (fun, env) -> {
                        var functionName = fun.name().orElse("lambda");
                        var invoker = new JSObject.Invoker() {
                            @Override
                            public Object invoke(JSObject self, Object receiver, Object... args) {
                                if(fun.parameters().size() != args.length) {
                                    throw new Failure("wrong number of parameters at line " + fun.lineNumber() + ":" + functionName);
                                }
                                var newEnv = JSObject.newEnv(env);
                                newEnv.register("this", receiver);
                                for(int index = 0; index < args.length; index++) {
                                    newEnv.register(fun.parameters().get(index), args[index]);
                                }
                                try {
                                    return visit(fun.body(), newEnv);
                                } catch (ReturnError error) {
                                    return error.getValue();
                                }
                            }
                        };
                        var function = JSObject.newFunction(functionName, invoker);
                        fun.name().ifPresent(name -> env.register(name, function));
                        return function;
                    })
                    .when(Return.class, (_return, env) -> {
                        var value = visit(_return.expr(), env);
                        throw new ReturnError(value);
                    })
                    .when(If.class, (_if, env) -> {
                        var condition = visit(_if.condition(), env);
                        if(condition.equals(0)) {
                            return visit(_if.falseBlock(), env);
                        }
                        return visit(_if.trueBlock(), env);
                    })
                    .when(New.class, (_new, env) -> {
                        var prototype = JSObject.newObject(null);
                        _new.initMap().forEach((property, init) -> {
                            var value = visit(init, env);
                            prototype.register(property, value);
                        });
                        return prototype;
                    })
                    .when(FieldAccess.class, (fieldAccess, env) -> {
                        var prototype = visit(fieldAccess.receiver(), env);
                    })
                    .when(FieldAssignment.class, (fieldAssignment, env) -> {
                        throw new UnsupportedOperationException("TODO FieldAssignment");
                    })
                    .when(MethodCall.class, (methodCall, env) -> {
                        throw new UnsupportedOperationException("TODO MethodCall");
                    })
            ;

    @SuppressWarnings("unchecked")
    public static void interpret(Script script, PrintStream outStream) {
        JSObject globalEnv = JSObject.newEnv(null);
        Block body = script.body();
        globalEnv.register("global", globalEnv);
        globalEnv.register("print", JSObject.newFunction("print", (self, receiver, args) -> {
            System.err.println("print called with " + Arrays.toString(args));
            outStream.println(Arrays.stream(args).map(Object::toString).collect(Collectors.joining(" ")));
            return UNDEFINED;
        }));
        globalEnv.register("+", JSObject.newFunction("+", (self, receiver, args) -> (Integer) args[0] + (Integer) args[1]));
        globalEnv.register("-", JSObject.newFunction("-", (self, receiver, args) -> (Integer) args[0] - (Integer) args[1]));
        globalEnv.register("/", JSObject.newFunction("/", (self, receiver, args) -> (Integer) args[0] / (Integer) args[1]));
        globalEnv.register("*", JSObject.newFunction("*", (self, receiver, args) -> (Integer) args[0] * (Integer) args[1]));
        globalEnv.register("%", JSObject.newFunction("%", (self, receiver, args) -> (Integer) args[0] * (Integer) args[1]));

        globalEnv.register("==", JSObject.newFunction("==", (self, receiver, args) -> args[0].equals(args[1]) ? 1 : 0));
        globalEnv.register("!=", JSObject.newFunction("!=", (self, receiver, args) -> !args[0].equals(args[1]) ? 1 : 0));
        globalEnv.register("<", JSObject.newFunction("<",   (self, receiver, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) < 0) ? 1 : 0));
        globalEnv.register("<=", JSObject.newFunction("<=", (self, receiver, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) <= 0) ? 1 : 0));
        globalEnv.register(">", JSObject.newFunction(">",   (self, receiver, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) > 0) ? 1 : 0));
        globalEnv.register(">=", JSObject.newFunction(">=", (self, receiver, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) >= 0) ? 1 : 0));
        visit(body, globalEnv);
    }
}

/**
 *
 * Exercise 1 - AST Interpreter
 *  1. The visitor allows us to "visit" all instructions
 *  First we visit the root of the tree and then we will visit all instruction
 *  If an instruction has different components (it is a node) we will visit each one
 *  Parameter ENV : all variables that are known within and outside the scope of the block
 *  (there is no "let" variables)
 *
 * 2. the localVarAccess had to be implemented
 *
 */
