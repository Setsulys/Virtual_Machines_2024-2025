package fr.umlv.smalljs.astinterp;

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
import fr.umlv.smalljs.rt.Failure;
import fr.umlv.smalljs.rt.JSObject;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static fr.umlv.smalljs.rt.JSObject.UNDEFINED;
import static java.util.stream.Collectors.joining;

public final class ASTInterpreter {
  private static JSObject asJSObject(Object value, int lineNumber) {
    if (!(value instanceof JSObject jsObject)) {
      throw new Failure("at line " + lineNumber + ", type error " + value + " is not a JSObject");
    }
    return jsObject;
  }

  static Object visit(Expr expression, JSObject env) {
    return switch (expression) {
      case Block(List<Expr> instrs, int lineNumber) -> {
				//throw new UnsupportedOperationException("TODO Block");
        // TODO loop over all instructions
        for(var instr :instrs){
          visit(instr,env);
        }
        yield UNDEFINED;
      }
      case Literal<?>(Object value, int lineNumber) -> {
        //throw new UnsupportedOperationException("TODO Literal");
        yield value;
      }
      case FunCall(Expr qualifier, List<Expr> args, int lineNumber) -> {
        //throw new UnsupportedOperationException("TODO FunCall");
        var qualifierValue = visit(qualifier,env);
        if(!(qualifierValue instanceof JSObject jsObject)){
          throw new Failure("not a function");
        }
        var values = args.stream().map(e-> visit(e,env)).toArray();
        yield jsObject.invoke(UNDEFINED,values);
      }
      case LocalVarAccess(String name, int lineNumber) -> {
        //throw new UnsupportedOperationException("TODO LocalVarAccess");
        yield env.lookup(name);
      }
      case LocalVarAssignment(String name, Expr expr, boolean declaration, int lineNumber) -> {
        //throw new UnsupportedOperationException("TODO LocalVarAssignment");
        var expVisit = visit(expr,env);
        if(declaration && env.lookup(name)!=UNDEFINED) {
          throw new Failure("variable "+name+" already defined");
        }
        env.register(name,expVisit);
        yield expVisit;
      }
      case Fun(Optional<String> optName, List<String> parameters, Block body, int lineNumber) -> {
        //throw new UnsupportedOperationException("TODO Fun");
//        var functionName = optName.orElse("lambda");
        JSObject.Invoker invoker = new JSObject.Invoker() {
          @Override
          public Object invoke(Object receiver, Object... args) {
            // check the arguments length
            if(args.length!=parameters.size()){
              throw new Failure("wrong numbers of argument");
            }
            // create a new environment
            var newEnv = JSObject.newEnv(env);
            // add this and all the parameters
            newEnv.register("this",receiver);
            // visit the body
            for (int i = 0; i < args.length; i++) {
              newEnv.register(parameters.get(i),args[i]);
            }
            try {
              return visit(body, newEnv);
            }catch (ReturnError returnError){
              return returnError.getValue();
            }
          }
        };
         //create the JS function with the invoker
        var function=JSObject.newFunction(optName.orElse("lambda"),invoker);
         //register it if necessary
        optName.ifPresent(name->{
          env.register(name,function);
        });
         //yield the function
        yield function;
      }
      case Return(Expr expr, int lineNumber) -> {
        //throw new UnsupportedOperationException("TODO Return");
        var value = visit(expr,env);
        throw new ReturnError(value);
      }
      case If(Expr condition, Block trueBlock, Block falseBlock, int lineNumber) -> {
        //throw new UnsupportedOperationException("TODO If");
        var value = visit(condition,env);
        if(value instanceof Integer i && i==0){
          visit(falseBlock,env);
        }
        else{
          visit(trueBlock,env);
        }
        yield UNDEFINED;
      }
      case New(Map<String, Expr> initMap, int lineNumber) -> {
				throw new UnsupportedOperationException("TODO New");
      }
      case FieldAccess(Expr receiver, String name, int lineNumber) -> {
        throw new UnsupportedOperationException("TODO FieldAccess");
      }
      case FieldAssignment(Expr receiver, String name, Expr expr, int lineNumber) -> {
        throw new UnsupportedOperationException("TODO FieldAssignment");
      }
      case MethodCall(Expr receiver, String name, List<Expr> args, int lineNumber) -> {
        throw new UnsupportedOperationException("TODO MethodCall");
      }
    };
  }

  @SuppressWarnings("unchecked")
  private static JSObject createGlobalEnv(PrintStream outStream) {
    JSObject globalEnv = JSObject.newEnv(null);
    globalEnv.register("global", globalEnv);
    globalEnv.register("print", JSObject.newFunction("print", (_, args) -> {
      System.err.println("print called with " + Arrays.toString(args));
      outStream.println(Arrays.stream(args).map(Object::toString).collect(Collectors.joining(" ")));
      return UNDEFINED;
    }));
    globalEnv.register("+", JSObject.newFunction("+", (_, args) -> (Integer) args[0] + (Integer) args[1]));
    globalEnv.register("-", JSObject.newFunction("-", (_, args) -> (Integer) args[0] - (Integer) args[1]));
    globalEnv.register("/", JSObject.newFunction("/", (_, args) -> (Integer) args[0] / (Integer) args[1]));
    globalEnv.register("*", JSObject.newFunction("*", (_, args) -> (Integer) args[0] * (Integer) args[1]));
    globalEnv.register("%", JSObject.newFunction("%", (_, args) -> (Integer) args[0] % (Integer) args[1]));
    globalEnv.register("==", JSObject.newFunction("==", (_, args) -> args[0].equals(args[1]) ? 1 : 0));
    globalEnv.register("!=", JSObject.newFunction("!=", (_, args) -> !args[0].equals(args[1]) ? 1 : 0));
    globalEnv.register("<", JSObject.newFunction("<", (_, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) < 0) ? 1 : 0));
    globalEnv.register("<=", JSObject.newFunction("<=", (_, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) <= 0) ? 1 : 0));
    globalEnv.register(">", JSObject.newFunction(">", (_, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) > 0) ? 1 : 0));
    globalEnv.register(">=", JSObject.newFunction(">=", (_, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) >= 0) ? 1 : 0));
    return globalEnv;
  }

  public static void interpret(Script script, PrintStream outStream) {
    JSObject globalEnv =createGlobalEnv(outStream);
    Block body = script.body();
    visit(body, globalEnv);
  }
}

