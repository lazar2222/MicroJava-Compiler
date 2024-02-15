package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.*;

import java.util.ArrayList;

import org.apache.logging.log4j.Logger;
import rs.ac.bg.etf.pp1.util.LoggingUtils;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class SemanticAnalyzer extends VisitorAdaptor
{
	public static Logger log = LoggingUtils.getLogger();
	
	public SemanticAnalyzer()
	{
		EST.init();
		SemanticAnalysisHelper.semAn = this;
	}
	
	//Analyzer context
	public boolean errorDetected = false;
	public Obj DeclaredType = null;
	public Obj DeclaredClass = null;
	public Obj DeclaredMethod = null;
	public boolean inClass = false;
	public boolean inMethod = false;
	public boolean hasBaseConstructor = false;
	public boolean methodCall = false;
	public int LoopCounter = 0;
	public int ctorCount = 0; 
	public int argCount = 0;
	public int globaloffset = 0;
	public int classoffset = 0;
	public int methodoffset = 0;
	
    public void visit(CompleteProgram node) 
    {
    	Obj program = new Obj(Obj.Prog, node.getI1(), EST.noType,0,0);
    	SemanticAnalysisHelper.symbolDeclaration(program,node.getLine());
    	program = EST.insert(program);
    	EST.openScope();
    	globaloffset = 0;
    	
    	node.childrenAccept(this);
    	
    	EST.chainLocalSymbols(program);
    	EST.closeScope();
    	SemanticAnalysisHelper.checkMainValid(program);
	}
    
    public void visit(ProgramDeclarationList node) { visit_a(node); }
    public void visit(ProgramDeclarationEpsilon node) { visit_a(node); }
    public void visit(ProgramConstDeclaration node) { visit_a(node); }
    public void visit(ProgramVarDeclaration node) { visit_a(node); }
    public void visit(ProgramClassDeclaration node) { visit_a(node); }
    public void visit(ProgramMethodDeclarationList node) { visit_a(node); }
    public void visit(ProgramMethodDeclarationEpsilon node) { visit_a(node); }
    public void visit(ConstDeclaration node) { visit_a(node); }
    public void visit(ConstAssignmentSingle node) { visit_a(node); }
    public void visit(ConstAssignmentList node) { visit_a(node); }
    
    public void visit(ConstAssignment node)
    {	
    	node.childrenAccept(this);
    	
    	SemanticAnalysisHelper.constTypeCheck((Obj)node.getAnyConst().object,node.getLine());
    	Obj con = new Obj(Obj.Con,node.getI1(),DeclaredType.getType(),((Obj)node.getAnyConst().object).getAdr(),0);
    	SemanticAnalysisHelper.symbolDeclaration(con, node.getLine());
    	EST.insert(con);
    }
    
    public void visit(IntConst node) 
    { 
    	node.object = new Obj(Obj.Con, "int", EST.intType, node.getI1(), 0);
	}
    
    public void visit(CharConst node) 
    { 
    	node.object = new Obj(Obj.Con, "char", EST.charType, node.getC1(), 0); 
	}
    
    public void visit(BoolConst node) 
    {
    	node.object = new Obj(Obj.Con, "bool", EST.boolType, node.getB1()?1:0, 0);
	}
    
    public void visit(VarDeclaration node) { visit_a(node); }
    public void visit(VarDeclaration2 node) { visit_a(node); }
    public void visit(TypelessDeclarationSingle node) { visit_a(node); }
    public void visit(TypelessDeclarationList node) { visit_a(node); }
    public void visit(TypelessDeclarationSingle2 node) { visit_a(node); }
    public void visit(TypelessDeclarationList2 node) { visit_a(node); }
    
    
    public void visit(TypelessVarDeclaration node) 
    {
		Obj var = new Obj(inMethod?Obj.Var:inClass?Obj.Fld:Obj.Var,node.getI1(),DeclaredType.getType(),inMethod?methodoffset++:inClass?classoffset++:globaloffset++,0);
		SemanticAnalysisHelper.symbolDeclaration(var, node.getLine());
		EST.insert(var);
	}
    
    public void visit(TypelessArrayDeclaration node) 
    {
		Struct at = new Struct(Struct.Array, DeclaredType.getType());
		Obj var = new Obj(inMethod?Obj.Var:inClass?Obj.Fld:Obj.Var,node.getI1(),at,inMethod?methodoffset++:inClass?classoffset++:globaloffset++,0);
		SemanticAnalysisHelper.symbolDeclaration(var, node.getLine());
		EST.insert(var);
    }
    
    public void visit(ClassDeclaration node) 
    {
    	node.getClassIdent().accept(this);
    	Obj cls = (Obj) node.getClassIdent().object;
    	SemanticAnalysisHelper.symbolDeclaration(cls, node.getLine());
    	cls = EST.insert(cls);
    	EST.openScope();
    	inClass = true;
    	hasBaseConstructor = false;
    	DeclaredClass = cls;
    	ctorCount = 0;
    	classoffset = 1;
    	
    	SemanticAnalysisHelper.classPrologue(cls);
    	
    	node.getVarDecls().accept(this);
    	EST.chainLocalSymbols(cls.getType());
    	node.getClassBody().accept(this);
    	
    	inClass = false;
    	EST.chainLocalSymbols(cls.getType());
    	EST.closeScope();
	}
    
    public void visit(BaseClass node) 
    {
    	Struct cls = new Struct(Struct.Class,EST.noType);
    	cls.setElementType(EST.noType);
    	node.object = new Obj(Obj.Type,node.getI1(),cls,0,0);
	}
    
    public void visit(DerivedClass node)
    {
    	node.childrenAccept(this);
    	SemanticAnalysisHelper.checkBaseClass(DeclaredType,node.getLine());
    	Struct cls = new Struct(Struct.Class,DeclaredType.getType());
    	cls.setElementType(DeclaredType.getType());
    	node.object = new Obj(Obj.Type,node.getI1(),cls,0,0);
	}
    
    public void visit(VarDeclarationList node) { visit_a(node); }
    public void visit(VarDeclarationEpsilon node) { visit_a(node); }
    
    public void visit(ClassBodyList node) 
    {
    	node.getConstructorDecls().accept(this);
    	SemanticAnalysisHelper.postConstructorDeclarations(DeclaredClass);
    	node.getMethodDecls().accept(this);
    	SemanticAnalysisHelper.postMethodDeclarations(DeclaredClass);
	}
    
    public void visit(ClassBodyEpsilon node) 
    {
    	node.childrenAccept(this);
    	SemanticAnalysisHelper.postConstructorDeclarations(DeclaredClass);
    	SemanticAnalysisHelper.postMethodDeclarations(DeclaredClass);
	}
    
    public void visit(ConstructorDeclarationList node) { visit_a(node); }
    public void visit(ConstructorDeclarationEpsilon node) { visit_a(node); }
    
    public void visit(ConstructorDeclaration node) 
    {
    	SemanticAnalysisHelper.checkConstructorName(node.getI1(),node.getLine());
    	hasBaseConstructor = true;
    	Obj ctor = new Obj(Obj.Meth, "ctor"+ctorCount, EST.noType,0,0);
    	ctorCount++;
    	SemanticAnalysisHelper.symbolDeclaration(ctor, node.getLine());
    	ctor = EST.insert(ctor);
    	DeclaredMethod = ctor;
		EST.openScope();
		EST.insert(new Obj(Obj.Var, "this", DeclaredClass.getType(),0,0));
		argCount = 1;
		methodoffset = 1;
		inMethod = true;
		
		node.getFormParsOrNone().accept(this);
		
		ctor.setLevel(argCount);
		
		node.getVarDecls().accept(this);
		EST.chainLocalSymbols(ctor);
    	node.getStatements().accept(this);
		
		inMethod = false;
		ctor.setLevel(argCount);
		EST.chainLocalSymbols(ctor);
		EST.closeScope();
		EST.chainLocalSymbols(DeclaredClass.getType());
		SemanticAnalysisHelper.checkConstructor(ctor,node.getLine());
	}
    
    public void visit(HasFormalParameters node) { visit_a(node); }
    public void visit(HasNoFormalParameters node) { visit_a(node); }
    public void visit(HasStatements node) { visit_a(node); }
    public void visit(HasNoStatements node) { visit_a(node); }
    
    public void visit(MethodDeclaration node)
    {
    	node.getReturnType().accept(this);
    	Obj meth = new Obj(Obj.Meth, node.getI2(), DeclaredType.getType(),0,0);
    	SemanticAnalysisHelper.symbolDeclaration(meth, node.getLine());
    	meth = EST.insert(meth);
    	DeclaredMethod = meth;
		EST.openScope();
		argCount = 0;
		methodoffset = 0;
		inMethod = true;
		if(inClass)
		{
			EST.insert(new Obj(Obj.Var, "this", DeclaredClass.getType(),0,0));
			argCount++;
			methodoffset++;
		}
		
    	node.getFormParsOrNone().accept(this);
    	
    	meth.setLevel(argCount);
    	
    	node.getVarDecls().accept(this);
    	EST.chainLocalSymbols(meth);
    	node.getStatements().accept(this);
		
		inMethod = false;
		EST.chainLocalSymbols(meth);
		EST.closeScope();
		if(inClass) 
		{
			EST.chainLocalSymbols(DeclaredClass.getType());
			SemanticAnalysisHelper.checkOverride(meth,node.getLine());
		}
	}
    
    public void visit(ReturnNonVoid node) { visit_a(node); }
    
    public void visit(ReturnVoid node) 
    {
    	DeclaredType = new Obj(Obj.Type, "void", EST.noType);
    }
    
    public void visit(FormalParameterSingle node) { visit_a(node); }
    public void visit(FormalParameterList node) { visit_a(node); }
    
    public void visit(FormalParameter node)
    {
    	argCount++;
    	node.childrenAccept(this);
	}
    
    public void visit(Type node)
    {
    	DeclaredType = EST.find(node.getI1());
    	SemanticAnalysisHelper.checkType(DeclaredType, node.getI1(), node.getLine());
	}
    
    public void visit(DesignatorStmt node) { visit_a(node); }
    
    public void visit(IfStmt node) 
    {
    	node.getCondition().accept(this);
    	SemanticAnalysisHelper.checkStrictType((Obj)node.getCondition().object, EST.boolType, "bool", node.getLine());
    	node.getStatement().accept(this);
	}
    
    public void visit(IfElseStmt node) 
    {
    	node.getCondition().accept(this);
    	SemanticAnalysisHelper.checkStrictType((Obj)node.getCondition().object, EST.boolType, "bool", node.getLine());
    	node.getStatement().accept(this);
    	node.getStatement().accept(this);
	}
    
    public void visit(WhileStmt node) 
    {
    	node.getCondition().accept(this);
    	SemanticAnalysisHelper.checkStrictType((Obj)node.getCondition().object, EST.boolType, "bool", node.getLine());
    	LoopCounter++;
    	node.getStatement().accept(this);
    	LoopCounter--;
	}
    
    public void visit(BreakStmt node) 
    {
    	SemanticAnalysisHelper.checkInLoop("break",node.getLine()); 
	}
    
    public void visit(ContinueStmt node) 
    {
    	SemanticAnalysisHelper.checkInLoop("continue",node.getLine()); 
	}
    
    public void visit(ReturnExprStmt node) 
    {
    	node.childrenAccept(this);
    	SemanticAnalysisHelper.checkInMethodNotConstructor(node.getLine());
   	 	SemanticAnalysisHelper.checkReturnType(((Obj)node.getExpr().object).getType(),node.getLine());
	}
    
    public void visit(ReturnVoidStmt node) 
    {
    	 SemanticAnalysisHelper.checkInMethodNotConstructor(node.getLine());
    	 SemanticAnalysisHelper.checkReturnType(EST.noType,node.getLine());
	}
    
    public void visit(ReadStmt node) 
    {
    	node.childrenAccept(this);
    	SemanticAnalysisHelper.checkLValue((Obj)node.getDesignator().object,node.getLine());
    	SemanticAnalysisHelper.checkPrimitive((Obj)node.getDesignator().object,node.getLine());
	}
    
    public void visit(PrintExprStmt node) 
    {
    	node.childrenAccept(this);
    	SemanticAnalysisHelper.checkPrimitive((Obj)node.getExpr().object,node.getLine());
	}
    
    public void visit(PrintExprConstStmt node) 
    {
    	node.childrenAccept(this);
    	SemanticAnalysisHelper.checkPrimitive((Obj)node.getExpr().object,node.getLine());
	}
    
    public void visit(ForeachStmt node) 
    {
    	node.getDesignator().accept(this);
    	Obj designator = (Obj)node.getDesignator().object;
    	SemanticAnalysisHelper.checkArray(designator, node.getLine());
    	Obj ident = EST.find(node.getI2());
    	SemanticAnalysisHelper.checkDeclared(ident, node.getI2(), node.getLine());
    	SemanticAnalysisHelper.checkVar(ident, node.getLine());
    	SemanticAnalysisHelper.checkStrictType(ident, designator.getType().getElemType(), designator.getName(), node.getLine());
    	node.getStatement().accept(this);
	}
    
    public void visit(BlockStmt node) { visit_a(node); }
    
    public void visit(Assignment node) 
    {
    	node.childrenAccept(this);
    	SemanticAnalysisHelper.checkLValue((Obj)node.getDesignator().object,node.getLine());
    	SemanticAnalysisHelper.checkAsignable((Obj)node.getDesignator().object,(Obj)node.getExpr().object,node.getLine());
	}
    
    public void visit(FunctionCall node) 
    {
    	node.getDesignator().accept(this);
    	SemanticAnalysisHelper.checkCallable((Obj)node.getDesignator().object,node.getLine());
    	Obj CalledMethod = (Obj)node.getDesignator().object;
    	boolean method = methodCall;
    	
    	node.getActParsOrNone().accept(this);
    	
    	SemanticAnalysisHelper.checkArguments(CalledMethod,(ArrayList<Obj>)node.getActParsOrNone().object,method,node.getLine());
    	SemanticAnalysisHelper.DecodeFunctionCall(CalledMethod,method,node.getLine());
	}
    
    public void visit(PostIncrement node) 
    {
    	node.childrenAccept(this);
    	SemanticAnalysisHelper.checkLValue((Obj)node.getDesignator().object,node.getLine());
    	SemanticAnalysisHelper.checkStrictType((Obj)node.getDesignator().object,EST.intType,"int",node.getLine());
	}
    
    public void visit(PostDecrement node) 
    {
    	node.childrenAccept(this);
    	SemanticAnalysisHelper.checkLValue((Obj)node.getDesignator().object,node.getLine());
    	SemanticAnalysisHelper.checkStrictType((Obj)node.getDesignator().object,EST.intType,"int",node.getLine());
	}
    
    public void visit(CompoundAssignment node) 
    {
    	node.getDesignator().accept(this);
    	Obj CompoundType =(Obj)node.getDesignator().object;
    	SemanticAnalysisHelper.checkArray(CompoundType,node.getLine());
    	node.getDesignators().object = new ArrayList<Obj>();
    	node.getDesignators().accept(this);
    	SemanticAnalysisHelper.checkDesignators((ArrayList<Obj>)node.getDesignators().object,CompoundType,node.getLine());
	}
    
    public void visit(HasActualParameters node) 
    {
    	node.object = new ArrayList<Obj>();
    	node.getActPars().object = node.object;
    	node.childrenAccept(this);
	}
    
    public void visit(HasNoActualParameters node) 
    {
    	node.object = new ArrayList<Obj>();
	}
    
    public void visit(DesignatorSingle node)
    {
    	node.getDesignatorOrNone().object = node.object;
    	node.childrenAccept(this);
	}
    public void visit(DesignatorList node) 
    {
    	node.getDesignatorOrNone().object = node.object;
    	node.getDesignators().object = node.object;
    	node.childrenAccept(this); 
	}
    
    public void visit(DesignatorActual node) 
    { 
    	node.childrenAccept(this);
    	((ArrayList<Obj>)node.object).add((Obj)node.getDesignator().object);
	}
    
    public void visit(DesignatorEpsilon node) 
    {
    	((ArrayList<Obj>)node.object).add(null);
	}
    
	public void visit(ActualParameterSingle node) 
    {
    	node.getExpr().accept(this);
    	((ArrayList<Obj>)node.object).add((Obj)node.getExpr().object);
	}
    
    public void visit(ActualParameterList node) 
    {
    	node.getExpr().accept(this);
    	((ArrayList<Obj>)node.object).add((Obj)node.getExpr().object);
    	node.getActPars().object = node.object;
    	node.getActPars().accept(this);
	}
    
    public void visit(SimpleCondition node) 
    {
    	node.childrenAccept(this);
    	node.object = node.getCondTerm().object;
	}
    
    public void visit(ComplexCondition node) 
    {
    	node.childrenAccept(this);
    	node.object = node.getCondTerm().object; 
	}
    
    public void visit(SimpleConditionTerm node) 
    {
    	node.childrenAccept(this);
    	node.object = node.getCondFact().object;
	}
    
    public void visit(ComplexConditionTerm node) 
    {
    	node.childrenAccept(this);
    	node.object = node.getCondFact().object;
	}
    
    public void visit(ExpressionConditionFact node) 
    {
    	node.childrenAccept(this);
    	SemanticAnalysisHelper.checkStrictType((Obj)node.getExpr().object, EST.boolType, "bool", node.getLine());
    	node.object = node.getExpr().object;
	}
    
    public void visit(RelopConditionFact node) 
    {
    	node.childrenAccept(this);
    	SemanticAnalysisHelper.checkComparable((Obj)node.getExpr().object,(Obj)node.getExpr1().object,(String)node.getRelop().object,node.getLine());
    	node.object = new Obj(Obj.Con, "bool", EST.boolType);
	}
    
    public void visit(NegativeExpression node) 
    {
    	node.childrenAccept(this);
    	SemanticAnalysisHelper.checkStrictType((Obj)node.getTerm().object, EST.intType, "int", node.getLine());
    	node.object = node.getTerm().object;
    }
    
    public void visit(PositiveExpression node)
    {
    	node.childrenAccept(this);
    	node.object = node.getTerm().object;
	}
    
    public void visit(SimpleExpression node) 
    {
    	node.childrenAccept(this);
    	node.object = node.getTerms().object;
	}
    
    public void visit(ComplexExpression node)
    {
    	node.childrenAccept(this);
    	SemanticAnalysisHelper.checkStrictType((Obj)node.getExpr().object, EST.intType, "int", node.getLine());
    	SemanticAnalysisHelper.checkStrictType((Obj)node.getTerms().object, EST.intType, "int", node.getLine());
    	node.object = node.getTerms().object;
	}
    
    public void visit(SimpleTerm node) 
    {
    	node.childrenAccept(this);
    	node.object = node.getFactor().object;
	}
    
    public void visit(ComplexTerm node) 
    {
    	node.childrenAccept(this);
    	SemanticAnalysisHelper.checkStrictType((Obj)node.getTerm().object, EST.intType, "int", node.getLine());
    	SemanticAnalysisHelper.checkStrictType((Obj)node.getFactor().object, EST.intType, "int", node.getLine());
    	node.object = node.getFactor().object;
	}
    
    public void visit(Variable node) 
    {
    	node.childrenAccept(this);
    	node.object = node.getDesignator().object;
	}
    
    public void visit(FunctionCallInExpression node) 
    {
    	node.getDesignator().accept(this);
    	SemanticAnalysisHelper.checkCallable((Obj)node.getDesignator().object,node.getLine());
    	SemanticAnalysisHelper.checkReturnNonVoid((Obj)node.getDesignator().object,node.getLine());
    	Obj CalledMethod = (Obj)node.getDesignator().object;
    	boolean method = methodCall;
    	
    	node.getActParsOrNone().accept(this);
    	
    	SemanticAnalysisHelper.checkArguments(CalledMethod,(ArrayList<Obj>)node.getActParsOrNone().object,method,node.getLine());
    	SemanticAnalysisHelper.DecodeFunctionCall(CalledMethod,method,node.getLine());
    	node.object = new Obj(Obj.Con, "function return", ((Obj)node.getDesignator().object).getType());
    }
    
    public void visit(Literal node) 
    {
    	node.childrenAccept(this);
    	node.object = node.getAnyConst().object;
	}
    
    public void visit(NewArray node)
    {
    	node.getType().accept(this);
    	Struct at = new Struct(Struct.Array, DeclaredType.getType());
    	node.getExpr().accept(this);
    	SemanticAnalysisHelper.checkStrictType((Obj)node.getExpr().object, EST.intType, "int", node.getLine());
    	node.object = new Obj(Obj.Con, "new array", at);
    	
	}
    public void visit(NewObject node)
    {
    	node.getType().accept(this);
    	SemanticAnalysisHelper.checkBaseClass(DeclaredType, node.getLine());
    	node.getActParsOrNone().accept(this);
    	
    	SemanticAnalysisHelper.checkValidConstructor(DeclaredType.getType(),(ArrayList<Obj>)node.getActParsOrNone().object,node.getLine());
    	SemanticAnalysisHelper.decodeNewObject(DeclaredType,node.getLine());
    	node.object = new Obj(Obj.Con, "new object", DeclaredType.getType());
	}
    
    public void visit(ExpressionInParentheses node) 
    {
    	node.childrenAccept(this);
    	node.object = node.getExpr().object;
	}
    
    public void visit(VarDesignator node) 
    {
    	Obj ident = EST.find(node.getI1());
    	SemanticAnalysisHelper.checkDeclared(ident, node.getI1(), node.getLine());
    	SemanticAnalysisHelper.DecodeVarDesignator(ident,node);
    	node.object = ident;
    	methodCall=(ident.getKind()==Obj.Meth && ((ident.getLocalSymbols().toArray().length > 0) && ((Obj)ident.getLocalSymbols().toArray()[0]).getName().equals("this")));
	}
    
    public void visit(FieldDesignator node) 
    {
    	node.getDesignator().accept(this);
    	Obj ident = (Obj)node.getDesignator().object;
    	SemanticAnalysisHelper.checkBaseClass(ident, node.getLine());
    	Obj member = SemanticAnalysisHelper.getMember(ident,node.getI2(),node.getLine());
    	SemanticAnalysisHelper.decodeFieldDesignator(member,node);
    	node.object = member;
    	methodCall = member.getKind() == Obj.Meth;
	}
    
    public void visit(ArrayDesignator node) 
    {
    	node.getDesignator().accept(this);
    	Obj ident = (Obj)node.getDesignator().object;
    	SemanticAnalysisHelper.checkArray(ident, node.getLine());
    	node.getExpr().accept(this);
    	SemanticAnalysisHelper.checkStrictType((Obj)node.getExpr().object, EST.intType, "int", node.getLine());
    	SemanticAnalysisHelper.DecodeArrayDesignator(ident,node);
    	node.object = new Obj(Obj.Elem, "array element",ident.getType().getElemType());
    	methodCall=false;
	}
    
    public void visit(Label node) { visit_a(node); }
    public void visit(Assign node) { visit_a(node); }
    
    public void visit(Equal node) 
    {
    	node.object = "==";
	}
    
    public void visit(NotEqual node)
    {
    	node.object = "!="; 
	}
    
    public void visit(Greater node)
    {
    	node.object = ">";
	}
    
    public void visit(GreaterEqual node)
    {
    	node.object = ">=";
	}
    
    public void visit(Less node)
    {
    	node.object = "<";
	}
    
	public void visit(LessEqual node)
	{
		node.object = "<=";
	}
    
	public void visit(Add node) { visit_a(node); }
    public void visit(Sub node) { visit_a(node); }
    public void visit(Mul node) { visit_a(node); }
    public void visit(Div node) { visit_a(node); }
    public void visit(Mod node) { visit_a(node); }
	
	public void visit_a(SyntaxNode node){node.childrenAccept(this);}
	
}
