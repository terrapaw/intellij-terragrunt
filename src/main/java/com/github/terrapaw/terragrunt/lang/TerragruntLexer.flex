// Terragrunt HCL Lexer — see docs/architecture.md for design decisions
package com.github.terrapaw.terragrunt.lang;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import static com.github.terrapaw.terragrunt.lang.psi.TerragruntTypes.*;
import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;

%%

%class TerragruntLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType

%state STRING
%state INTERPOLATION
%state BLOCK_COMMENT_STATE
%state HEREDOC_ID
%state HEREDOC_BODY

%{
  private String heredocId = null;
  private java.util.Deque<Integer> stateStack = new java.util.ArrayDeque<>();
  private int interpolationBraceDepth = 0;

  private void pushState(int state) {
    stateStack.push(yystate());
    yybegin(state);
  }

  private void popState() {
    if (!stateStack.isEmpty()) {
      yybegin(stateStack.pop());
    } else {
      yybegin(YYINITIAL);
    }
  }
%}

NEWLINE = \r\n | \r | \n
WS = [ \t]+
DIGIT = [0-9]
NUMBER = {DIGIT}+ ("." {DIGIT}+)? ([eE][+-]? {DIGIT}+)?
ID_START = [a-zA-Z_]
ID_CONTINUE = [a-zA-Z0-9_\-]
IDENTIFIER = {ID_START}{ID_CONTINUE}*
LINE_COMMENT = ("#" | "//") [^\r\n]*
ESCAPE_SEQ = \\[nrt\"\\] | \\u[0-9a-fA-F]{4} | \\U[0-9a-fA-F]{8}

%%

<YYINITIAL> {
  {WS}                    { return WHITE_SPACE; }
  {NEWLINE}               { return WHITE_SPACE; }
  {LINE_COMMENT}          { return LINE_COMMENT; }
  "/*"                    { pushState(BLOCK_COMMENT_STATE); return BLOCK_COMMENT; }

  // Heredoc
  "<<-" / {IDENTIFIER}    { heredocId = null; yybegin(HEREDOC_ID); return HEREDOC_START; }
  "<<" / {IDENTIFIER}     { heredocId = null; yybegin(HEREDOC_ID); return HEREDOC_START; }

  // String
  \"                      { pushState(STRING); return QUOTE; }

  // Keywords
  "true"    { return TRUE; }
  "false"   { return FALSE; }
  "null"    { return NULL; }
  "for"     { return FOR; }
  "in"      { return IN; }
  "if"      { return IF; }
  "else"    { return ELSE; }
  "endif"   { return ENDIF; }
  "endfor"  { return ENDFOR; }

  // Multi-char operators
  "=>"                    { return FAT_ARROW; }
  "=="                    { return EQEQ; }
  "!="                    { return NEQ; }
  "<="                    { return LTEQ; }
  ">="                    { return GTEQ; }
  "&&"                    { return AND; }
  "||"                    { return OR; }
  "..."                   { return ELLIPSIS; }
  "${"                    { return INTERPOLATION_START; }
  "%{"                    { return DIRECTIVE_START; }

  // Single-char operators and delimiters
  "{"                     { return LBRACE; }
  "}"                     { return RBRACE; }
  "["                     { return LBRACKET; }
  "]"                     { return RBRACKET; }
  "("                     { return LPAREN; }
  ")"                     { return RPAREN; }
  "="                     { return EQUALS; }
  "."                     { return DOT; }
  ","                     { return COMMA; }
  ":"                     { return COLON; }
  "?"                     { return QUESTION; }
  "*"                     { return STAR; }
  "+"                     { return PLUS; }
  "-"                     { return MINUS; }
  "/"                     { return SLASH; }
  "%"                     { return PERCENT; }
  "<"                     { return LT; }
  ">"                     { return GT; }
  "!"                     { return NOT; }

  {NUMBER}                { return NUMBER; }
  {IDENTIFIER}            { return IDENTIFIER; }

  [^]                     { return BAD_CHARACTER; }
}

<BLOCK_COMMENT_STATE> {
  "*/"                    { popState(); return BLOCK_COMMENT; }
  [^*]+                   { return BLOCK_COMMENT; }
  "*"                     { return BLOCK_COMMENT; }
}

<STRING> {
  \"                      { popState(); return QUOTE; }
  {ESCAPE_SEQ}            { return STRING_LITERAL; }
  "$${"                   { return STRING_LITERAL; }
  "%%{"                   { return STRING_LITERAL; }
  "${"                    { interpolationBraceDepth = 0; pushState(INTERPOLATION); return INTERPOLATION_START; }
  "%{"                    { interpolationBraceDepth = 0; pushState(INTERPOLATION); return DIRECTIVE_START; }
  [^\"\\\$\%\r\n]+        { return STRING_LITERAL; }
  [\$\%]                  { return STRING_LITERAL; }
  \\                      { return STRING_LITERAL; }
  {NEWLINE}               { popState(); return BAD_CHARACTER; }
}

<INTERPOLATION> {
  {WS}                    { return WHITE_SPACE; }
  {NEWLINE}               { return WHITE_SPACE; }

  // Nested string inside interpolation
  \"                      { pushState(STRING); return QUOTE; }

  // Track brace depth for nested objects
  "{"                     { interpolationBraceDepth++; return LBRACE; }
  "}"                     {
                            if (interpolationBraceDepth > 0) {
                              interpolationBraceDepth--;
                              return RBRACE;
                            }
                            // End of interpolation - return to string
                            popState();
                            return INTERPOLATION_END;
                          }

  // Keywords
  "true"    { return TRUE; }
  "false"   { return FALSE; }
  "null"    { return NULL; }
  "for"     { return FOR; }
  "in"      { return IN; }
  "if"      { return IF; }
  "else"    { return ELSE; }

  // Operators
  "=>"                    { return FAT_ARROW; }
  "=="                    { return EQEQ; }
  "!="                    { return NEQ; }
  "<="                    { return LTEQ; }
  ">="                    { return GTEQ; }
  "&&"                    { return AND; }
  "||"                    { return OR; }
  "..."                   { return ELLIPSIS; }

  "["                     { return LBRACKET; }
  "]"                     { return RBRACKET; }
  "("                     { return LPAREN; }
  ")"                     { return RPAREN; }
  "="                     { return EQUALS; }
  "."                     { return DOT; }
  ","                     { return COMMA; }
  ":"                     { return COLON; }
  "?"                     { return QUESTION; }
  "*"                     { return STAR; }
  "+"                     { return PLUS; }
  "-"                     { return MINUS; }
  "/"                     { return SLASH; }
  "%"                     { return PERCENT; }
  "<"                     { return LT; }
  ">"                     { return GT; }
  "!"                     { return NOT; }

  {NUMBER}                { return NUMBER; }
  {IDENTIFIER}            { return IDENTIFIER; }

  [^]                     { return BAD_CHARACTER; }
}

<HEREDOC_ID> {
  {IDENTIFIER}            { heredocId = yytext().toString(); yybegin(HEREDOC_BODY); return IDENTIFIER; }
  {WS}                    { return WHITE_SPACE; }
  {NEWLINE}               { return WHITE_SPACE; }
  [^]                     { return BAD_CHARACTER; }
}

<HEREDOC_BODY> {
  "${"                    { interpolationBraceDepth = 0; pushState(INTERPOLATION); return INTERPOLATION_START; }
  "%{"                    { interpolationBraceDepth = 0; pushState(INTERPOLATION); return DIRECTIVE_START; }
  "$${"                   { return HEREDOC_CONTENT; }
  "%%{"                   { return HEREDOC_CONTENT; }
  {NEWLINE}               { return HEREDOC_CONTENT; }
  [^\r\n\$\%]+            {
                            String text = yytext().toString().trim();
                            if (heredocId != null && text.equals(heredocId)) {
                              heredocId = null;
                              yybegin(YYINITIAL);
                              return HEREDOC_END;
                            }
                            return HEREDOC_CONTENT;
                          }
  [\$\%]                  { return HEREDOC_CONTENT; }
}
