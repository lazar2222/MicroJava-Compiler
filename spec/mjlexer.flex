package rs.ac.bg.etf.pp1;
import java_cup.runtime.Symbol;
import org.apache.logging.log4j.Logger;
import rs.ac.bg.etf.pp1.util.LoggingUtils;
%%

%{
	private Symbol symbol(int type) 
	{
		return new Symbol(type, yyline+1, yycolumn, yytext());
	}
	
	private Symbol symbol(int type, Object val) 
	{
		return new Symbol(type, yyline+1, yycolumn, val);
	}
%}

%cup
%line
%column

%eofval{
	return symbol(sym.EOF);
%eofval}

WSPACE = \t|\r|\n|\b|\f|" "
IDENT = [a-zA-Z][a-zA-Z0-9_]*
INTLIT = [0-9]+
CHARLIT = '[ -~]'
BOOLLIT = true|false

%%

program		{return symbol(sym.PROGRAM);}
break		{return symbol(sym.BREAK);}
class		{return symbol(sym.CLASS);}
else		{return symbol(sym.ELSE);}
const		{return symbol(sym.CONST);}
if			{return symbol(sym.IF);}
while		{return symbol(sym.WHILE);}
new			{return symbol(sym.NEW);}
print		{return symbol(sym.PRINT);}
read		{return symbol(sym.READ);}
return		{return symbol(sym.RETURN);}
void		{return symbol(sym.VOID);}
extends		{return symbol(sym.EXTENDS);}
continue	{return symbol(sym.CONTINUE);}
foreach		{return symbol(sym.FOREACH);}

{INTLIT}	{return symbol(sym.INTLIT,Integer.valueOf(yytext()));}
{CHARLIT}	{return symbol(sym.CHARLIT,Character.valueOf(yytext().charAt(1)));}
{BOOLLIT}	{return symbol(sym.BOOLLIT,Boolean.valueOf(yytext().equals("true")?true:false));}

{IDENT}		{return symbol(sym.IDENT,yytext());}

"++"		{return symbol(sym.INC);}
"--"		{return symbol(sym.DEC);}
"=="		{return symbol(sym.EQ);}
"!="		{return symbol(sym.NE);}
">="		{return symbol(sym.GE);}
"<="		{return symbol(sym.LE);}
"=>"		{return symbol(sym.RARROW);}
"&&"		{return symbol(sym.AND);}
"||"		{return symbol(sym.OR);}
"+"			{return symbol(sym.ADD);}
"-"			{return symbol(sym.SUB);}
"*"			{return symbol(sym.MUL);}
"/"			{return symbol(sym.DIV);}
"%"			{return symbol(sym.MOD);}
">"			{return symbol(sym.GT);}
"<"			{return symbol(sym.LT);}
"="			{return symbol(sym.ASSIGN);}
";"			{return symbol(sym.SEMI);}
":"			{return symbol(sym.COLON);}
","			{return symbol(sym.COMMA);}
"."			{return symbol(sym.PERIOD);}
"("			{return symbol(sym.LPAR);}
")"			{return symbol(sym.RPAR);}
"["			{return symbol(sym.LBRACK);}
"]"			{return symbol(sym.RBRACK);}
"{"			{return symbol(sym.LBRACE);}
"}"			{return symbol(sym.RBRACE);}

"//"[^\n]*	{}

{WSPACE}	{}

. 			{LoggingUtils.getLogger().error("Neocekivan token '" + yytext() + "' na liniji " + (yyline+1) + ", koloni " + yycolumn);}