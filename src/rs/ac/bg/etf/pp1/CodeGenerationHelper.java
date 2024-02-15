package rs.ac.bg.etf.pp1;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Stack;

import org.apache.logging.log4j.Logger;

import rs.ac.bg.etf.pp1.util.LoggingUtils;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class CodeGenerationHelper
{
	public static Logger log = LoggingUtils.getLogger();
	public static CodeGenerator cg = null;
	
	public static byte[] buf = new byte[8192]; // prostor za smestanje prevedenog programskog koda
	public static int pc = 0;
	
	public CodeGenerationHelper()
	{
		
	}
	
	public static void put (int x)  
	{
		buf[pc++] = (byte)x;
	}

	public static void put2 (int x) { put(x>>8); put(x);  }
	public static void put4 (int x) { put2(x>>16); put2(x); }
	
	public static void loadConst (int n) 
	{
	    if (0<=n&&n<=5) put (Code.const_n+n);
	    else if (n==-1) put (Code.const_m1);
	    else  { put(Code.const_); put4 (n); }
	  }
	
	public static void generateVFT(Struct cls) 
	{
		LinkedHashMap<String, Integer> methodToAddresMap = new LinkedHashMap<>();
		Stack<Struct> inheritanceStack = new Stack<>();
		//Walk the inheritance tree
		for(Struct c = cls; c != null; c = c.getElemType()) 
		{
			inheritanceStack.push(c);
		}
		while(!inheritanceStack.empty()) 
		{
			cls = inheritanceStack.pop();
			for(Obj m : cls.getMembers()) 
			{
				if(m.getKind() == Obj.Meth && !m.getName().startsWith("ctor")) 
				{
					//Insert if not exists, update if exists
					methodToAddresMap.put(m.getName(), m.getAdr());
				}
			}
		}
		//Writebytecode
		for(Entry<String,Integer> kvp: methodToAddresMap.entrySet()) 
		{
			String name = kvp.getKey();
			int address = kvp.getValue();
			//Put name
			for(int i = 0; i < name.length();i++) 
			{
				loadConst(name.charAt(i));
				put(Code.putstatic); 
				put2(cg.StaticIndex++);
			}
			loadConst(-1);
			put(Code.putstatic);
			put2(cg.StaticIndex++);
			
			loadConst(address);
			put(Code.putstatic);
			put2(cg.StaticIndex++);
		}
		loadConst(-2);
		put(Code.putstatic); 
		put2(cg.StaticIndex++);
	}
	
	public static void generate_start()
	{
		Code.mainPc = Code.pc;
		Code.put(Code.enter);Code.put(0);Code.put(0);
		//Load VFT
		
		for(Entry<String,Integer> e : cg.classToVFTP.entrySet())
		{
			log.trace("VFTP "+e.getKey()+"@"+e.getValue());
		}
		
		log.trace("VFTP dump");
		for(int i = 0; i < pc;) 
		{
			if(buf[i]==Code.const_) 
			{
				log.trace(((buf[i+6]<<8) + buf[i+7])+": "+buf[i+4]+" ("+(char)buf[i+4]+")");
				i+=8;
			}
			else if(buf[i]==Code.const_m1) 
			{
				log.trace((((int)buf[i+3]<<8) + (int)buf[i+4])+": -1 ("+(char)-1+")");
				i+=4;
			}
			else 
			{
				i+=4;
			}
		}
		
		
		for(int i = 0; i < pc;i++) 
		{
			Code.put(buf[i]);
		}
		
		//Jump to main
		absoluteCall(cg.mainAddr);
		Code.put(Code.exit);
		Code.put(Code.return_);
		
		Code.dataSize = cg.StaticIndex;
	}

	public static void generate_chr()
	{
		EST.chrObj.setAdr(Code.pc);
		Code.put(Code.enter);
		Code.put(1);
		Code.put(1);
		Code.put(Code.load_n + 0);
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	public static void generate_ord()
	{
		EST.ordObj.setAdr(Code.pc);
		Code.put(Code.enter);
		Code.put(1);
		Code.put(1);
		Code.put(Code.load_n + 0);
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	public static void generate_len()
	{
		EST.lenObj.setAdr(Code.pc);
		Code.put(Code.enter);
		Code.put(1);
		Code.put(1);
		Code.put(Code.load_n + 0);
		Code.put(Code.arraylength);
		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	public static Obj FindSymInScope(String object)
	{
		for(Obj o : cg.Scope) 
		{
			if(o.getName().equals(object)) 
			{
				return o;
			}
		}
		return EST.noObj;
	}
	
	public static Obj lookup(String name)
	{
		if(cg.GeneratedMethod!=null) 
		{
			for(Obj o : cg.GeneratedMethod.getLocalSymbols()) 
			{
				if(o.getName().equals(name)) 
				{
					return o;
				}
			}
		}
		for(Obj o : cg.Scope) 
		{
			if(o.getName().equals(name)) 
			{
				return o;
			}
		}
		for(Obj o : cg.Program.getLocalSymbols()) 
		{
			if(o.getName().equals(name)) 
			{
				return o;
			}
		}
		return EST.find(name);
	}

	public static Obj classLookup(Struct type, String name)
	{
		for(Obj o : type.getMembers()) 
		{
			if(o.getName().equals(name)) 
			{
				return o;
			}
		}
		return EST.noObj;
	}
	
	public static void prologue()
	{
		cg.GeneratedMethod.setAdr(Code.pc);
		Code.put(Code.enter);
		Code.put(cg.GeneratedMethod.getLevel());
		Code.put(cg.GeneratedMethod.getLocalSymbols().size());
	}

	public static void epilogue()
	{
		if(cg.GeneratedMethod.getType()!=EST.noType)
		{
			Code.put(Code.trap);
			Code.put(10);
		}
		else 
		{
			Code.put(Code.exit);
			Code.put(Code.return_);
		}
	}

	public static void generateImplicitCtor()
	{
		cg.GeneratedMethod = CodeGenerationHelper.FindSymInScope("ctor0");
		if(cg.GeneratedMethod.getAdr()==-1) 
		{
			prologue();
			epilogue();
		}
	}
	
	public static boolean loadable(Obj o) 
	{
		return o.getKind() == Obj.Con || o.getKind() == Obj.Var; 
	}

	public static boolean isMethod(Obj sym)
	{
		return (sym.getKind()==Obj.Meth && ((sym.getLocalSymbols().toArray().length > 0) && ((Obj)sym.getLocalSymbols().toArray()[0]).getName().equals("this")));
	}

	public static int typeSize(Struct type)
	{
		int res = 4;
		for(Obj o : type.getMembers()) 
		{
			if(o.getKind() == Obj.Fld) 
			{
				res +=4;
			}
		}
		return res;
	}
	
	public static Obj getValidConstructor(Struct type, ArrayList<Obj> object)
	{
		for(Obj o : type.getMembers()) 
		{
			if(o.getKind() == Obj.Meth && o.getName().startsWith("ctor")) 
			{
				if(SemanticAnalysisHelper.checkArgumentsSilent(o, object,0)) 
				{
					return o;
				}
			}
		}
		return EST.noObj;
	}
	
	public static void absoluteCall(int adr) 
	{
		Code.put(Code.call);
		Code.put2(adr-Code.pc+1);
	}

	public static void virtualCall(Obj obj)
	{
		if(isMethod(obj)) 
		{
			Code.put(Code.getfield);
			Code.put2(0);
			Code.put(Code.invokevirtual);
			for(int i=0;i<obj.getName().length();i++) 
			{
				Code.put4(obj.getName().charAt(i));
			}
			Code.put4(-1);
		}
		else 
		{
			absoluteCall(obj.getAdr());
		}
	}

	public static void line(int line)
	{
		log.trace("Na liniji "+line+" pc "+Code.pc);
	}
}
