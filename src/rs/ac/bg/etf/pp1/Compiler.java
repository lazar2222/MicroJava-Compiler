package rs.ac.bg.etf.pp1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;

import java_cup.runtime.Symbol;

import org.apache.logging.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.Program;
import rs.ac.bg.etf.pp1.util.LoggingUtils;
import rs.etf.pp1.mj.runtime.Code;

public class Compiler
{
	private static Logger log = LoggingUtils.getLogger(Compiler.class);
	
	public static void main(String[] args) 
	{
		String outPath="test/program.obj";
		
		if(args.length < 1) 
		{
			log.error("Ulazni fajl nije specifikovan");
			return;
		}
		if(args.length<2) 
		{
			if(args[0].endsWith(".mj"))
			{
				log.warn("Izlazni fajl nije specifikovan, koristi se ulazni fajl sa ekstenzijom obj");
				outPath = args[0].replace(".mj", ".obj");
			}
		}
		else 
		{
			outPath = args[1];
		}
		if(args.length==3) 
		{
			SemanticAnalysisHelper.skipprint=true;
		}
		String path = args[0];
		BufferedReader br = null;
		
		try 
		{
			 br = new BufferedReader(new FileReader(path));
		}
		catch(FileNotFoundException e)
		{
			log.error("Nije moguce otvoriti fajl: "+path);
			return;
		}
		
		log.info("Pocetak prevodjenja fajla: "+path);
		boolean res = Compile(br);
		
		if(br!=null){try{br.close();}catch(IOException e){e.printStackTrace();}}
		
		if(res) 
		{
			File objFile = new File(outPath);
			if(objFile.exists()) objFile.delete();
			
			try
			{
				Code.write(new FileOutputStream(objFile));
			}
			catch(FileNotFoundException e)
			{
				log.error("Greska pri pisanju u fajl: "+outPath);
				return;
			}
			log.info("Generisan objektni fajl: "+outPath);
		}
	}
	
	public static boolean Compile(BufferedReader r)
	{
		Yylex lexer = new Yylex(r);
		MJParser p = new MJParser(lexer);
		
		Symbol s = null;
		try 
		{
			s = p.parse();
		}
		catch(Exception e)
		{
			log.error("Doslo je do greske pri parsiranju, ne prelazi se na semanticku analizu");
			return false;
		}
		
		if(s == null || !(s.value instanceof Program) || p.errorDetected)
		{
			log.error("Doslo je do greske pri parsiranju, ne prelazi se na semanticku analizu");
			return false;
		}
		
		Program prog = (Program)(s.value);
		log.info("Leksicka i sintaksna analiza uspesne");
		log.info("Apstraktno sintaksno stablo:");
		log.info(prog.toString(""));
		
		log.info("Pokrenuta semanticka analiza");
		SemanticAnalyzer semAn = new SemanticAnalyzer();
		prog.accept(semAn);
		
		if(semAn.errorDetected)
		{
			tsdump();
			log.error("Doslo je do greske pri semantickoj analizi, ne prelazi se na generisanje koda");
			return false;
		}
		
		log.info("Pokrenuto generisanje koda");
		CodeGenerator cg = new CodeGenerator(semAn);
		prog.accept(cg);
		
		tsdump();
		
		if(Code.greska) 
		{
			log.error("Doslo je do greske pri generisanju koda, objektni fajl nije sacuvan");
			return false;
		}
		log.info("Generisanje koda uspesno zavrseno");
		return true;
	}
	
	public static void tsdump()
	{
		if(!SemanticAnalysisHelper.skipprint)
		{
			EST.dump();
		}
	}
}
