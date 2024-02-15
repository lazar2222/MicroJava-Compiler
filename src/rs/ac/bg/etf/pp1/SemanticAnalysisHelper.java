package rs.ac.bg.etf.pp1;

import java.util.ArrayList;

import org.apache.logging.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.Designator;
import rs.ac.bg.etf.pp1.ast.FieldDesignator;
import rs.ac.bg.etf.pp1.ast.SyntaxNode;
import rs.ac.bg.etf.pp1.util.LoggingUtils;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.visitors.DumpSymbolTableVisitor;

public class SemanticAnalysisHelper
{
	public static Logger log = LoggingUtils.getLogger();
	public static SemanticAnalyzer semAn = null;
	public static boolean additionalLogging = false;
	public static boolean skipprint = false;
	
	public static void SemanticWarning(String msg, int line)
	{
		if(additionalLogging) 
		{
			if(line != -1) 
			{
				log.warn(msg+", na liniji "+line);	
			}
			else 
			{
				log.warn(msg);
			}
		}
	}
	
	public static void SemanticError(String msg, int line)
	{
		if(line != -1) 
		{
			log.error(msg+", na liniji "+line);	
		}
		else 
		{
			log.error(msg);
		}
		semAn.errorDetected = true;
	}
	
	public static void SemanticInfo(String msg, int line)
	{
		if(additionalLogging) 
		{
			if(line != -1) 
			{
				log.info(msg+", na liniji "+line);	
			}
			else 
			{
				log.info(msg);
			}
		}
	}
	
	public static void SemanticUsageInfo(String message, Obj node, int line)
	{
		DumpSymbolTableVisitor dst = new DumpSymbolTableVisitor();
		if(!skipprint) 
		{
			node.accept(dst);
		}
		boolean old = additionalLogging;
		additionalLogging = true;
		SemanticInfo(message+" "+node.getName(), line);
		SemanticInfo(dst.getOutput().replace('\n', '\t'), -1);
		additionalLogging = old;
	}
	
	public static boolean equivalent(Struct a,Struct b)
	{
		return a==b || (a.getKind()==Struct.Array && b.getKind()==Struct.Array && equivalent(a.getElemType(), b.getElemType()));
	}
	
	public static boolean compatible(Struct a,Struct b) 
	{
		return equivalent(a, b) || (a.isRefType() && b == EST.nullType) || (a == EST.nullType && b.isRefType());
	}
	
	public static boolean assignable(Struct dst, Struct src)
	{
		return equivalent(dst, src) || (dst.isRefType() && src == EST.nullType) || (src.getKind() == Struct.Array && dst.getKind() == Struct.Array && dst.getElemType() == EST.noType) || superclass(dst,src);
	}
	
	public static boolean superclass(Struct a, Struct b) 
	{
        for (Struct s = b; s != null; s = s.getElemType()) {
            if (equivalent(s, a)) 
                  return true;
        }
        return false;
	}
	
	public static void symbolDeclaration(Obj program, int line)
	{
		Obj sameName = EST.find(program.getName());
		if(sameName!=EST.noObj) 
		{
			if(EST.currentScope().findSymbol(program.getName())!=null)
			{
				SemanticError("Redeklaracija simbola: "+program.getName(), line);
			}
			else
			{
				SemanticWarning("Simbol "+program.getName()+" sakriva simbol sa istim imenom iz okruzujuceg opsega", line);
			}
		}
	}
	
	public static void constTypeCheck(Obj literal, int line)
	{
		if(!equivalent(semAn.DeclaredType.getType(), literal.getType()))
		{
			SemanticError("Tip konstante "+semAn.DeclaredType.getName()+" nije ekvivalentan tipu literala "+literal.getName(), line);
		}
	}
	
	public static void checkType(Obj type,String name, int line)
	{
		if(type==EST.noObj)
		{
			SemanticError("Tip "+name+" nije definisan", line);
		}
		else if(type.getKind() != Obj.Type)
		{
			SemanticError(name+" nije ime tipa", line);
		}
	}
	
	public static void checkDeclared(Obj symbol,String name, int line)
	{
		if(symbol==EST.noObj)
		{
			SemanticError("Identifikator "+name+" nije deklarisan", line);
		}
	}
	
	public static void checkBaseClass(Obj type, int line)
	{
		if(type.getType().getKind() != Struct.Class)
		{
			SemanticError(type.getName()+" nije klasnog tipa", line);
		}
	}
	
	public static void classPrologue(Obj cls)
	{
		if(cls.getType().getElemType()!=EST.noType)
		{
			for(Obj o : cls.getType().getElemType().getMembers())
			{
				if(o.getKind() == Obj.Fld)
				{
					EST.insert(new Obj(Obj.Fld,o.getName(),o.getType(),o.getAdr(),0));
					semAn.classoffset++;
				}
			}
		}
		else
		{
			//EST.insert(Obj.Fld, "VFTP", EST.intType);
		}
	}

	public static void postConstructorDeclarations(Obj declaredClass)
	{
		if(!semAn.hasBaseConstructor)
		{
			SemanticInfo("Deklarise se implicitni konstruktor u klasi "+declaredClass.getName(), -1);
			Obj baseConstructor = EST.insert(new Obj(Obj.Meth, "ctor"+semAn.ctorCount, EST.noType,-1,0));
			EST.openScope();
			EST.insert(new Obj(Obj.Var, "this", declaredClass.getType(),0,0));
			baseConstructor.setLevel(1);
			EST.chainLocalSymbols(baseConstructor);
			EST.closeScope();
		}
	}

	public static void postMethodDeclarations(Obj declaredClass)
	{
	}

	public static void checkOverride(Obj meth, int line)
	{
		if(semAn.DeclaredClass.getType().getElemType()!=EST.noType)
		{
			//Is derived class
			for(Obj o :semAn.DeclaredClass.getType().getElemType().getMembers()) 
			{
				//Method attempts redefinition
				if(o.getName().equals(meth.getName())) 
				{
					SemanticInfo("Metod "+o.getName()+" klase "+semAn.DeclaredClass.getName()+" redefinise istoimeni metod bazne klase", line);
					if(!methodsMatchSignature(o, meth, true)) 
					{
						SemanticError("Metod "+o.getName()+" klase "+semAn.DeclaredClass.getName()+" pokusava da redefinise istoimeni metod bazne klase", line);
						SemanticError("Ali:", -1);
						methodsMatchSignature(o, meth, false);
					}
				}
			}
		}
	}
	
	public static boolean methodsMatchSignature(Obj methA, Obj methB,boolean silent) 
	{
		boolean ok = true;
		if(!assignable(methA.getType(), methB.getType())) 
		{
			if(!silent) {SemanticError("Povratni tipovi nisu kompatibilni", -1);}
			ok = false;
		}
		if(methA.getLevel() != methB.getLevel()) 
		{
			if(!silent) {SemanticError("Broj parametara nije isti", -1);}
			ok = false;
		}
		if(!ok) {return ok;}
		for(int i = 0; i < methA.getLevel(); i++)
		{
			Struct methAargtype = ((Obj)methA.getLocalSymbols().toArray()[i]).getType();
			Struct methBargtype = ((Obj)methB.getLocalSymbols().toArray()[i]).getType();
			if(i==0 && !assignable(methAargtype, methBargtype)) 
			{
				if(!silent) {SemanticError("Tip argumenata na poziciji "+i+" nije ekvivalentan", -1);}
				ok = false;
			}else if(i!=0 && !equivalent(methAargtype, methBargtype)) 
			{
				if(!silent) {SemanticError("Tip argumenata na poziciji "+i+" nije ekvivalentan", -1);}
				ok = false;
			}
		}
		return ok;
	}

	public static void checkConstructor(Obj ctor, int line)
	{
		for(Obj o :semAn.DeclaredClass.getType().getMembers()) 
		{
			if(o.getKind() == Obj.Meth && methodsMatchSignature(o, ctor, true) && !o.getName().equals(ctor.getName())) 
			{
				SemanticError("Ekvivalentan konstruktor vec postoji", line);
				return;
			}
		}
	}

	public static void checkConstructorName(String name, int line)
	{
		if(!name.equals(semAn.DeclaredClass.getName())) 
    	{
    		SemanticError("Konstruktor "+name+" mora imati isto ime kao klasa "+semAn.DeclaredClass.getName(), line);
    	}
	}

	public static void checkLValue(Obj object, int line)
	{
		if(object.getKind()!=Obj.Var && object.getKind()!=Obj.Fld && object.getKind()!=Obj.Elem)
		{
			SemanticError(object.getName()+" nije lvrednost", line);
		}
	}
	
	public static void checkVar(Obj object, int line)
	{
		if(object.getKind()!=Obj.Var)
		{
			SemanticError(object.getName()+" nije promenljiva", line);
		}
	}

	public static void checkAsignable(Obj object, Obj object2, int line)
	{
		if(!assignable(object.getType(), object2.getType())) 
		{
			SemanticError("Leva i desna strana izraza nisu kompatibilne pri dodeli", line);
		}
	}

	public static void checkStrictType(Obj object, Struct type,String display, int line)
	{
		if(!equivalent(object.getType(), type)) 
		{
			SemanticError(object.getName()+" nije ekvivalentnog tipa sa tipom "+display, line);
		}
	}

	public static void checkCallable(Obj object, int line)
	{
		if(object.getKind()!=Obj.Meth)
		{
			SemanticError(object.getName()+" nije funkcija", line);
		}
		if(object.getName().startsWith("ctor")) 
		{
			SemanticError("Konstruktor se moze pozvati samo naredbom new", line);
		}
	}
	
	public static void checkReturnNonVoid(Obj object, int line)
	{
		if(object.getType()==EST.noType) 
		{
			SemanticError("Void funkcija se ne moze koristiti u izrazu", line);
		}
	}

	public static void checkArray(Obj object, int line)
	{
		if(object.getType().getKind()!=Struct.Array)
		{
			SemanticError(object.getName()+"nije niz", line);
		}
	}

	public static void checkInLoop(String string, int line)
	{
		if(semAn.LoopCounter == 0) 
		{
			SemanticError(string+" se moze naci samo unutar petlji", line);
		}
	}

	public static void checkPrimitive(Obj object, int line)
	{
		if(object.getType()!=EST.intType && object.getType()!=EST.charType && object.getType()!=EST.boolType)
		{
			SemanticError(object.getName()+" nije osnovnog tipa", line);
		}
	}

	public static void checkInMethodNotConstructor(int line)
	{
		if(!semAn.inMethod || semAn.DeclaredMethod.getName().startsWith("ctor")) 
		{
			SemanticError("return naredba se moze naci samo unutar funkcija", line);
		}
	}

	public static void checkReturnType(Struct type, int line)
	{
		if(!equivalent(type, semAn.DeclaredMethod.getType())) 
		{
			SemanticError("Tip u return naredbi nije ekvivalentan tipu povratne vrednosti funkcije", line);
		}
	}

	public static void checkMainValid(Obj program)
	{
		for(Obj o : program.getLocalSymbols())
		{
			if(o.getName().equals("main") && o.getKind() == Obj.Meth) 
			{
				if(o.getType()!=EST.noType) 
				{
					SemanticError("main funkcija ne sme imati povratnu vrednost",-1);
					return;
				}
				if(o.getLevel()!=0) 
				{
					SemanticError("main funkcija ne sme imati parametre",-1);
					return;
				}
				return;
			}
		}
		SemanticError("Program mora sadrzati main funkciju",-1);
	}

	public static void checkComparable(Obj object, Obj object2, String object3, int line)
	{
		if(!compatible(object.getType(), object2.getType())) 
		{
			SemanticError(object.getName()+" i "+object2.getName() + "nisu kompatibilni", line);
		}
		if(object.getType()!=EST.intType && object.getType()!=EST.charType) 
		{
			if(!object3.equals("==") && !object3.equals("!=")) 
			{
				SemanticError("operatori == i != se mogu koristiti samo za tipove int i char", line);
			}
		}
	}

	public static void checkDesignators(ArrayList<Obj> object,Obj src, int line)
	{
		for(Obj o : object)
		{
			if(o!=null) 
			{
				checkLValue(o, line);
				checkAsignable(o, src, line);
			}
		}
	}

	public static void checkArguments(Obj calledMethod, ArrayList<Obj> args,boolean method, int line)
	{
		if(method) 
		{
			if((calledMethod.getLevel()-1)!=args.size()) 
			{
				SemanticError("funkcija "+calledMethod.getName()+" ocekuje "+(calledMethod.getLevel()-1)+" argumenata, navedeno "+args.size(), line);
				return;
			}
			for(int i = 0; i < args.size();i++) 
			{
				if(!assignable(((Obj)calledMethod.getLocalSymbols().toArray()[i+1]).getType(), args.get(i).getType())) 
				{
					SemanticError("argument "+(i+1)+" funkcije "+calledMethod.getName()+" nije kompatibilan sa navedenim argumentom", line);
				}
			}
		}
		else 
		{
			if(calledMethod.getLevel()!=args.size()) 
			{
				SemanticError("funkcija "+calledMethod.getName()+" ocekuje "+calledMethod.getLevel()+" argumenata, navedeno "+args.size(), line);
				return;
			}
			for(int i = 0; i < args.size();i++) 
			{
				if(!assignable(((Obj)calledMethod.getLocalSymbols().toArray()[i]).getType(), args.get(i).getType())) 
				{
					SemanticError("argument "+(i+1)+" funkcije "+calledMethod.getName()+" nije kompatibilan sa navedenim argumentom", line);
				}
			}
		}
		
	}
	public static boolean checkArgumentsSilent(Obj calledMethod, ArrayList<Obj> args, int line)
	{
		if((calledMethod.getLevel()-1)!=args.size()) 
		{
			return false;
		}
		for(int i = 0; i < args.size();i++) 
		{
			if(!assignable(((Obj)calledMethod.getLocalSymbols().toArray()[i+1]).getType(), args.get(i).getType())) 
			{
				return false;
			}
		}
		return true;
	}

	public static void checkValidConstructor(Struct type, ArrayList<Obj> object,int line)
	{
		for(Obj o : type.getMembers()) 
		{
			if(o.getKind() == Obj.Meth && o.getName().startsWith("ctor")) 
			{
				if(checkArgumentsSilent(o, object, line)) 
				{
					return;
				}
			}
		}
		SemanticError("nijedan konstruktor ne odgovara datim parametrima", line);
	}

	public static Obj getMember(Obj ident, String i2,int line)
	{
		for(Struct c = ident.getType();c!=null;c=c.getElemType()) 
		{
			if(c.getMembersTable().searchKey(i2)!=null) 
			{
				return c.getMembersTable().searchKey(i2);
			}
		}
		SemanticError("klasa "+ident.getName()+" ne sadrzi polje sa imenom "+i2, line);
		return EST.noObj;
	}

	public static void DecodeVarDesignator(Obj ident, SyntaxNode line)
	{
		if(line.getParent() instanceof Designator) {return;}
		if(ident.getKind() == Obj.Con) 
		{
			SemanticUsageInfo("Detektovana upotreba simbolicke konstante", ident, line.getLine());
		}
		else if(ident.getKind() == Obj.Var) 
		{
			if(ident.getLevel() == 0) 
			{
				SemanticUsageInfo("Detektovana upotreba globalne promenljive", ident, line.getLine());
			}
			else if(ident.getLevel() == 1 && ident.getAdr() >= semAn.DeclaredMethod.getLevel()) 
			{
				SemanticUsageInfo("Detektovana upotreba lokalne promenljive", ident, line.getLine());
			}
			else if(ident.getLevel() == 1 && ident.getAdr() < semAn.DeclaredMethod.getLevel()) 
			{
				SemanticUsageInfo("Detektovana upotreba formalnog parametra funkcije", ident, line.getLine());
			}
		}
		else if(ident.getKind() == Obj.Fld) 
		{
			SemanticUsageInfo("Detektovan pristup polju unutrasnje klase", ident, line.getLine());
		}
	}

	public static void DecodeFunctionCall(Obj calledMethod, boolean method, int line)
	{
		if(!method) 
		{
			SemanticUsageInfo("Detektovan poziv globalne funkcije", calledMethod, line);
		}
		else 
		{
			SemanticUsageInfo("Detektovan poziv metode unutrasnje klase", calledMethod, line);
		}
	}

	public static void DecodeArrayDesignator(Obj ident, SyntaxNode line)
	{
		if(line.getParent() instanceof Designator) {return;}
		SemanticUsageInfo("Detektovan pristup elementu niza", ident, line.getLine());
	}

	public static void decodeNewObject(Obj declaredType, int line)
	{
		SemanticUsageInfo("Detektovano kreiranje objekta unutrasnje klase", declaredType, line);
	}

	public static void decodeFieldDesignator(Obj member, FieldDesignator line)
	{
		if(line.getParent() instanceof Designator) {return;}
		if(member.getKind() == Obj.Fld) 
		{
			SemanticUsageInfo("Detektovan pristup polju unutrasnje klase", member, line.getLine());
		}
	}
	
	
}
