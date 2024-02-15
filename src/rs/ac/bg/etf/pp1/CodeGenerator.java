package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import org.apache.logging.log4j.Logger;
import rs.ac.bg.etf.pp1.util.LoggingUtils;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class CodeGenerator extends VisitorAdaptor
{
	public static Logger log = LoggingUtils.getLogger();
	
	public SemanticAnalyzer semAn;
	
	public CodeGenerator(SemanticAnalyzer sa)
	{
		CodeGenerationHelper.cg=this;
		semAn = sa;
	}
	
	//GeneratorContext
	
	public Obj Program = null;
	
	public Collection<Obj> Scope = null;
	public int ctorIndex = 0;
	public Obj GeneratedMethod = null;
	
	public Map<String, Integer> classToVFTP = new HashMap<>();
	public int StaticIndex = 0;
	public int mainAddr = 0;
	public int compoundIndex = 0;
	public int loopLevel = 0;
	public Stack<Integer> continueAddr = new Stack<>();
	public Stack<Integer> breakFixups = new Stack<>();
	public Stack<Integer> breaklevels = new Stack<>();
	
	//Utility visitors
	
    public void visit(CompleteProgram node) 
    {
    	StaticIndex = semAn.globaloffset;
    	Program = EST.find(node.getI1());
    	Scope = Program.getLocalSymbols();
    	CodeGenerationHelper.generate_chr();
    	CodeGenerationHelper.generate_ord();
    	CodeGenerationHelper.generate_len();
    	node.childrenAccept(this);
    	CodeGenerationHelper.generate_start();
	}
    
    public void visit(ClassDeclaration node) 
    {
    	node.getClassIdent().accept(this);
    	
    	classToVFTP.put((String)node.getClassIdent().object,StaticIndex);
    	Scope = CodeGenerationHelper.FindSymInScope((String)node.getClassIdent().object).getType().getMembers();
    	
    	ctorIndex = 0;
    	node.getVarDecls().accept(this);
    	CodeGenerationHelper.generateImplicitCtor();
    	node.getClassBody().accept(this);
    	
    	Scope = Program.getLocalSymbols();
    	CodeGenerationHelper.generateVFT(CodeGenerationHelper.FindSymInScope((String)node.getClassIdent().object).getType());
	}
    
    public void visit(BaseClass node) 
    {
    	node.object = node.getI1(); 
	}
    
    public void visit(DerivedClass node) 
    {
    	node.object = node.getI1(); 
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
    
    public void visit(Type node) 
    {
    	node.object = CodeGenerationHelper.lookup(node.getI1()); 
	}
    
    //Code generating visitors
    
    public void visit(ConstructorDeclaration node) 
    {
    	GeneratedMethod = CodeGenerationHelper.FindSymInScope("ctor"+ctorIndex);
    	CodeGenerationHelper.prologue();
    	node.childrenAccept(this);
    	CodeGenerationHelper.epilogue();
    	ctorIndex++;
	}
    
    public void visit(MethodDeclaration node) 
    {
    	if(node.getI2().equals("main")) 
    	{
    		mainAddr = Code.pc;
    	}
    	GeneratedMethod = CodeGenerationHelper.FindSymInScope(node.getI2());
    	CodeGenerationHelper.prologue();
    	node.childrenAccept(this);
    	CodeGenerationHelper.epilogue();
	}
    
    //Statement
    
    public void visit(DesignatorStmt node) 
    {
    	CodeGenerationHelper.line(node.getLine());
    	node.childrenAccept(this); 
	}
    
    public void visit(IfStmt node)
    {
    	CodeGenerationHelper.line(node.getLine());
    	node.getCondition().accept(this);
    	Code.loadConst(0);
    	int patch = Code.pc+1;
    	Code.putFalseJump(Code.ne, 0);
    	node.getStatement().accept(this);
    	Code.fixup(patch);
	}
    
    public void visit(IfElseStmt node) 
    {
    	CodeGenerationHelper.line(node.getLine());
    	node.getCondition().accept(this);
    	Code.loadConst(0);
    	int patch = Code.pc+1;
    	Code.putFalseJump(Code.ne, 0);
    	
    	node.getStatement().accept(this);
    	
    	int patch2 = Code.pc+1;
    	Code.putJump(0);
    	
    	Code.fixup(patch);
    	node.getStatement1().accept(this);
    	
    	Code.fixup(patch2);
	}
    
    public void visit(WhileStmt node)
    {
    	CodeGenerationHelper.line(node.getLine());
    	int cond = Code.pc;
    	continueAddr.push(cond);
    	node.getCondition().accept(this);
    	Code.loadConst(0);
    	int patch = Code.pc+1;
    	Code.putFalseJump(Code.ne, 0);
    	loopLevel++;
    	
    	node.getStatement().accept(this);
    	
    	loopLevel--;
    	Code.putJump(cond);
    	Code.fixup(patch);
    	while(!breaklevels.isEmpty() && breaklevels.peek()>loopLevel) 
    	{
    		breaklevels.pop();
    		Code.fixup(breakFixups.pop());
    	}
    	continueAddr.pop();
	}
    
    public void visit(BreakStmt node)
    {
    	CodeGenerationHelper.line(node.getLine());
    	breakFixups.add(Code.pc+1);
    	breaklevels.add(loopLevel);
    	Code.putJump(0); 
	}
    
    public void visit(ContinueStmt node)
    {
    	CodeGenerationHelper.line(node.getLine());
    	Code.putJump(continueAddr.peek()); 
	}
    
    public void visit(ReturnExprStmt node)
    {
    	CodeGenerationHelper.line(node.getLine());
    	node.childrenAccept(this);
    	Code.put(Code.exit);
    	Code.put(Code.return_);
	}
    
    public void visit(ReturnVoidStmt node)
    {
    	CodeGenerationHelper.line(node.getLine());
    	Code.put(Code.exit);
    	Code.put(Code.return_);
	}
    
    public void visit(ReadStmt node) 
    {
    	CodeGenerationHelper.line(node.getLine());
    	node.childrenAccept(this);
    	if(((Obj)node.getDesignator().object).getType()==EST.charType) 
    	{
    		Code.put(Code.bread);
    	}
    	else 
    	{
    		Code.put(Code.read);
    	}
    	Code.store((Obj)node.getDesignator().object);
	}
    
    public void visit(PrintExprStmt node)
    { 
    	CodeGenerationHelper.line(node.getLine());
    	node.childrenAccept(this);
    	if(((Obj)node.getExpr().object).getType()==EST.charType) 
    	{
    		Code.loadConst(1);
    		Code.put(Code.bprint);
    	}
    	else 
    	{
    		Code.loadConst(5);
    		Code.put(Code.print);
    	}
	}
    
    public void visit(PrintExprConstStmt node)
    {
    	CodeGenerationHelper.line(node.getLine());
    	node.childrenAccept(this);
    	if(((Obj)node.getExpr().object).getType()==EST.charType) 
    	{
    		Code.loadConst(node.getI2());
    		Code.put(Code.bprint);
    	}
    	else 
    	{
    		Code.loadConst(node.getI2());
    		Code.put(Code.print);
    	}
	}
    
    public void visit(ForeachStmt node) 
    {
    	CodeGenerationHelper.line(node.getLine());
    	//Init
    	Code.loadConst(0);
    	
    	//Condition
    	int cond = Code.pc;
    	continueAddr.push(cond);
    	Code.put(Code.dup);
    	node.getDesignator().accept(this);
    	Code.load((Obj)node.getDesignator().object);
    	Code.put(Code.arraylength);
    	int patch = Code.pc+1;
    	Code.putFalseJump(Code.lt, 0);
    	loopLevel++;
    	
    	//prologue
    	node.getDesignator().accept(this);
    	Code.load((Obj)node.getDesignator().object);
    	Code.put(Code.dup2);
    	Code.put(Code.pop);
    	if(((Obj)node.getDesignator().object).getType().getElemType()==EST.charType) 
    	{
    		Code.put(Code.baload);
    	}
    	else 
    	{
    		Code.put(Code.aload);
    	}
    	Code.store(CodeGenerationHelper.lookup(node.getI2()));
    	
    	//statements
    	node.getStatement().accept(this);
    	
    	//epilogue
    	Code.loadConst(1);
    	Code.put(Code.add);
    	
    	loopLevel--;
    	Code.putJump(cond);
    	Code.fixup(patch);
    	while(!breaklevels.isEmpty() && breaklevels.peek()>loopLevel) 
    	{
    		breaklevels.pop();
    		Code.fixup(breakFixups.pop());
    	}
    	continueAddr.pop();
    	Code.put(Code.pop);
	}
    
    public void visit(BlockStmt node) 
    {
    	CodeGenerationHelper.line(node.getLine());
    	node.childrenAccept(this); 
	}
    
    //DesignatorStatement
    
    public void visit(Assignment node) 
    {
    	node.childrenAccept(this);
    	Code.store((Obj)node.getDesignator().object);
	}
    
    public void visit(FunctionCall node) 
    {
    	node.childrenAccept(this);
    	node.getDesignator().accept(this);
	   	CodeGenerationHelper.virtualCall(((Obj)node.getDesignator().object));
	   	if(((Obj)node.getDesignator().object).getType()!=EST.noType)
	   	{
	   		Code.put(Code.pop);
	   	}
	}
    
    public void visit(PostIncrement node)
    {
    	node.getDesignator().accept(this);
    	node.getDesignator().accept(this);
    	Code.load((Obj)node.getDesignator().object);
    	Code.loadConst(1);
    	Code.put(Code.add);
    	Code.store((Obj)node.getDesignator().object);
	}
    
    public void visit(PostDecrement node) 
    {
    	node.getDesignator().accept(this);
    	node.getDesignator().accept(this);
    	Code.load((Obj)node.getDesignator().object);
    	Code.loadConst(-1);
    	Code.put(Code.add);
    	Code.store((Obj)node.getDesignator().object); 
	}
    
    public void visit(CompoundAssignment node) 
    {
    	compoundIndex = 0;
    	node.getDesignators().object = node.getDesignator();
    	node.getDesignators().accept(this);
	}
    
    //Designators
    
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
    	node.getDesignator().accept(this);
    	((Designator)node.object).accept(this);
    	((Designator)node.object).accept(this);
    	Code.load(((Obj)((Designator)node.object).object));
    	Code.put(Code.arraylength);
    	Code.loadConst(compoundIndex);
    	int patch = Code.pc+1;
    	Code.putFalseJump(Code.le, 0);
    	Code.put(Code.trap);
    	Code.put(20);
    	Code.fixup(patch);
    	Code.load(((Obj)((Designator)node.object).object));
    	Code.loadConst(compoundIndex);
    	if(((Obj)((Designator)node.object).object).getType().getElemType()==EST.charType) 
    	{
    		Code.put(Code.baload);
    	}
    	else 
    	{
    		Code.put(Code.aload);
    	}
    	Code.store((Obj)node.getDesignator().object);
    	compoundIndex++;
	}
    
    public void visit(DesignatorEpsilon node) 
    {
    	compoundIndex++;
	}
    
    //ActPars
    
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

    //Condition
    
    public void visit(SimpleCondition node)
    {
    	node.childrenAccept(this); 
	}
    
    public void visit(ComplexCondition node) 
    {
    	node.getCondTerm().accept(this);;
    	Code.loadConst(1);
    	int patch = Code.pc + 1;
    	Code.putFalseJump(Code.ne, mainAddr);
    	node.getCondition().accept(this);
    	int patch2 = Code.pc + 1;
    	Code.putJump(0);
    	Code.fixup(patch);
    	Code.loadConst(1);
    	Code.fixup(patch2);
    	
	}
    
    //ConditionTerm
    
    public void visit(SimpleConditionTerm node) 
    {
    	node.childrenAccept(this); 
	}
    
    public void visit(ComplexConditionTerm node) 
    {
    	node.getCondFact().accept(this);;
    	Code.loadConst(0);
    	int patch = Code.pc + 1;
    	Code.putFalseJump(Code.ne, mainAddr);
    	node.getCondTerm().accept(this);
    	int patch2 = Code.pc + 1;
    	Code.putJump(0);
    	Code.fixup(patch);
    	Code.loadConst(0);
    	Code.fixup(patch2);
	}
    
    //ConditionFact
    
    public void visit(ExpressionConditionFact node)
    {
    	node.childrenAccept(this);
	}
    
    public void visit(RelopConditionFact node) 
    {
    	node.getExpr().accept(this);
    	node.getExpr1().accept(this);
    	int patch = Code.pc + 1;
    	node.getRelop().accept(this);
    	Code.loadConst(1);
    	int patch2 = Code.pc + 1;
    	Code.putJump(0);
    	Code.fixup(patch);
    	Code.loadConst(0);
    	Code.fixup(patch2);
	}
    
    //Expression
    
    public void visit(NegativeExpression node) 
    {
    	node.childrenAccept(this);
    	Code.put(Code.neg);
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
    	node.getExpr().accept(this);
    	node.getTerms().accept(this);
    	node.getAddop().accept(this); 
    	node.object = node.getTerms().object;
	}
    
    //Term
    
    public void visit(SimpleTerm node) 
    {
    	node.childrenAccept(this);
    	node.object = node.getFactor().object;
	}
    
    public void visit(ComplexTerm node) 
    {
    	node.getTerm().accept(this);
    	node.getFactor().accept(this);
    	node.getMulop().accept(this);
    	node.object = node.getFactor().object;
	}
    
    //Factor
    
    public void visit(Variable node)
    {
    	node.childrenAccept(this);
    	Code.load((Obj)node.getDesignator().object);
    	node.object = node.getDesignator().object;
	}
    
    public void visit(FunctionCallInExpression node)
    {
    	node.childrenAccept(this);
    	node.getDesignator().accept(this);
	   	CodeGenerationHelper.virtualCall(((Obj)node.getDesignator().object));
	   	node.object = node.getDesignator().object;
	}
    
    public void visit(Literal node) 
    {
    	node.childrenAccept(this);
    	Obj o = (Obj)node.getAnyConst().object;
    	Code.loadConst(o.getAdr());
    	node.object = o;
	}
    
    public void visit(NewArray node) 
    {
    	 node.childrenAccept(this);
    	 Struct at = new Struct(Struct.Array, ((Obj)node.getType().object).getType());
    	 Code.put(Code.newarray);
    	 if(at.getElemType()==EST.charType) 
    	 {
    		 Code.put(0);
    	 }
    	 else 
    	 {
    		 Code.put(1);
    	 }
     	node.object = new Obj(Obj.Con, "new array", at);
	}
    
    public void visit(NewObject node) 
    {
		node.getType().accept(this);
	   	Obj cls = (Obj)node.getType().object;
	   	//Create
	   	Code.put(Code.new_);
	   	Code.put2(CodeGenerationHelper.typeSize(cls.getType()));
	   	//Link VFTP
	   	Code.put(Code.dup);
	   	Code.put(Code.dup);
	   	Code.loadConst(classToVFTP.get(cls.getName()));
	   	Code.put(Code.putfield);
	   	Code.put2(0);
	   	//Call ctor
	   	node.getActParsOrNone().accept(this);
	   	Obj ctor = CodeGenerationHelper.getValidConstructor(cls.getType(), (ArrayList<Obj>)node.getActParsOrNone().object);
	   	CodeGenerationHelper.absoluteCall(ctor.getAdr());
    	node.object = new Obj(Obj.Con, "new object", cls.getType());
	}
    
    public void visit(ExpressionInParentheses node)
    {
    	node.childrenAccept(this);
    	node.object = node.getExpr().object;
	}
    
    //Designator
    
    public void visit(VarDesignator node) 
    {
    	Obj sym = CodeGenerationHelper.lookup(node.getI1());
    	if(sym.getKind() == Obj.Fld || CodeGenerationHelper.isMethod(sym)) 
    	{
    		Code.put(Code.load_n + 0);
    	}
    	node.object = sym;
	}
    
    public void visit(FieldDesignator node) 
    {
    	node.childrenAccept(this);
    	Obj accessedObj = ((Obj)node.getDesignator().object);
		Code.load(accessedObj);
    	node.object = CodeGenerationHelper.classLookup(accessedObj.getType(),node.getI2());
	}
    
    public void visit(ArrayDesignator node) 
    {
    	node.getDesignator().accept(this);
    	Obj accessedObj = ((Obj)node.getDesignator().object);
    	Code.load(accessedObj);
    	node.getExpr().accept(this);
    	node.object = new Obj(Obj.Elem, "array element",accessedObj.getType().getElemType());
	}
    
    //Assignop
    
    public void visit(Assign node) { visit_a(node); }
    
    //Relop
    
    public void visit(Equal node) 
    {
    	Code.putFalseJump(Code.eq, 0);
	}
    
    public void visit(NotEqual node) 
    {
    	Code.putFalseJump(Code.ne, 0); 
	}
    
    public void visit(Greater node)
    {
    	Code.putFalseJump(Code.gt, 0); 
	}
    
    public void visit(GreaterEqual node)
    {
    	Code.putFalseJump(Code.ge, 0); 
	}
    
    public void visit(Less node)
    {
    	Code.putFalseJump(Code.lt, 0); 
	}
    
	public void visit(LessEqual node) 
	{
		Code.putFalseJump(Code.le, 0); 
	}
	
	//Addop
	
	public void visit(Add node) 
	{
		Code.put(Code.add); 
	}
	
    public void visit(Sub node) 
    {
    	Code.put(Code.sub); 
	}
    
    //Mulop
    
    public void visit(Mul node) 
    {
    	Code.put(Code.mul); 
	}
    
    public void visit(Div node) 
    {
    	Code.put(Code.div);
	}
    
    public void visit(Mod node) 
    {
    	Code.put(Code.rem);
	}
    
    //Default visitors
    
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
    public void visit(ConstAssignment node) { visit_a(node); }
    public void visit(VarDeclaration node) { visit_a(node); }
	public void visit(VarDeclaration2 node) { visit_a(node); }
    public void visit(TypelessDeclarationSingle node) { visit_a(node); }
    public void visit(TypelessDeclarationList node) { visit_a(node); }
	public void visit(TypelessDeclarationSingle2 node) { visit_a(node); }
	public void visit(TypelessDeclarationList2 node) { visit_a(node); }
    public void visit(TypelessVarDeclaration node) { visit_a(node); }
    public void visit(TypelessArrayDeclaration node) { visit_a(node); }
    public void visit(VarDeclarationList node) { visit_a(node); }
    public void visit(VarDeclarationEpsilon node) { visit_a(node); }
    public void visit(ClassBodyList node) { visit_a(node); }
    public void visit(ClassBodyEpsilon node) { visit_a(node); }
    public void visit(ConstructorDeclarationList node) { visit_a(node); }
    public void visit(ConstructorDeclarationEpsilon node) { visit_a(node); }
    public void visit(HasFormalParameters node) { visit_a(node); }
    public void visit(HasNoFormalParameters node) { visit_a(node); }
    public void visit(HasStatements node) { visit_a(node); }
    public void visit(HasNoStatements node) { visit_a(node); }
    public void visit(ReturnNonVoid node) { visit_a(node); }
    public void visit(ReturnVoid node) { visit_a(node); }
    public void visit(FormalParameterSingle node) { visit_a(node); }
    public void visit(FormalParameterList node) { visit_a(node); }
    public void visit(FormalParameter node) { visit_a(node); }
    public void visit(Label node) { visit_a(node); }
    
    public void visit_a(SyntaxNode node){node.childrenAccept(this);}
}
