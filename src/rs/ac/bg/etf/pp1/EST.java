package rs.ac.bg.etf.pp1;

import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class EST extends Tab 
{
	public static final Struct boolType = new Struct(Struct.Bool);
	
	public static void init() 
	{
		Tab.init();
		currentScope.addToLocals(new Obj(Obj.Type, "bool", boolType));
	}
	
	public static Obj insert(Obj obj)
	{
		Obj o = Tab.insert(obj.getKind(), obj.getName(), obj.getType());
		o.setAdr(obj.getAdr());
		return o;
	}
}
