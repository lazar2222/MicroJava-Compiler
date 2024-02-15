package rs.ac.bg.etf.pp1.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import java_cup.runtime.Symbol;
import rs.ac.bg.etf.pp1.sym;

public class SymbolUtils 
{
	public static String printSymbol(Symbol sym) 
	{
		if(sym == null){return "Printing null symbol";}
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("%-10s", symbolNameFromValue(sym.sym)));
		if(sym.value != null)
		{
			sb.append(" \"");
			sb.append(sym.value.toString());
			sb.append("\"");
		}
		return sb.toString();
	}
	
	private static String symbolNameFromValue (int value)
	{
		Class<sym> c = sym.class;
		for(Field f : c.getDeclaredFields())
		{
		    int mod = f.getModifiers();
		    if(Modifier.isStatic(mod) && Modifier.isPublic(mod) && Modifier.isFinal(mod))
		    {
		      try
		      {
		        if((int)f.get(null) == value) 
		        {
		        	return f.getName();
		        }
		      }
		      catch(Exception e){e.printStackTrace();}
		    }
	    }
		return "#"+value;
	}
}
