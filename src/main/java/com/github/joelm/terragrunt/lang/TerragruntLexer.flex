package com.github.joelm.terragrunt.lang;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import static com.github.joelm.terragrunt.lang.psi.TerragruntTypes.*;
import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;

%%

%class TerragruntLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType

%state STRING
%state BLOCK_COMMENT_STATE
%state HEREDOC_ID
%state HEREDOC_BODY

%{
  private String heredocId = null;
  private java.util.Deque<Integer> stateStack = new java.util.ArrayDeque<>();

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
  \"                      { pushState(STRING); return STRING_LITERAL; }

  // Keywords - must come before IDENTIFIER
  "true"  / [^a-zA-Z0-9_\-]  { return TRUE; }
  "false" / [^a-zA-Z0-9_\-]  { return FALSE; }
  "null"  / [^a-zA-Z0-9_\-]  { return NULL; }
  "for"   / [^a-zA-Z0-9_\-]  { return FOR; }
  "in"    / [^a-zA-Z0-9_\-]  { return IN; }
  "if"    / [^a-zA-Z0-9_\-]  { return IF; }
  "else"  / [^a-zA-Z0-9_\-]  { return ELSE; }
  "endif" / [^a-zA-Z0-9_\-]  { return ENDIF; }
  "endfor" / [^a-zA-Z0-9_\-] { return ENDFOR; }

  // Multi-char operators (must come before single-char)
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
  \"                      { popState(); return STRING_LITERAL; }
  {ESCAPE_SEQ}            { return STRING_LITERAL; }
  "$${"                   { return STRING_LITERAL; }
  "%%{"                   { return STRING_LITERAL; }
  "${"                    { return STRING_LITERAL; }
  "%{"                    { return STRING_LITERAL; }
  [^\"\\\$\%\r\n]+        { return STRING_LITERAL; }
  [\$\%]                  { return STRING_LITERAL; }
  \\                      { return STRING_LITERAL; }
  {NEWLINE}               { popState(); return BAD_CHARACTER; }
}

<HEREDOC_ID> {
  {IDENTIFIER}            { heredocId = yytext().toString(); yybegin(HEREDOC_BODY); return IDENTIFIER; }
  {WS}                    { return WHITE_SPACE; }
  {NEWLINE}               { return WHITE_SPACE; }
  [^]                     { return BAD_CHARACTER; }
}

<HEREDOC_BODY> {
  {NEWLINE}               { return HEREDOC_CONTENT; }
  [^\r\n]+                {
                            String text = yytext().toString().trim();
                            if (heredocId != null && text.equals(heredocId)) {
                              heredocId = null;
                              yybegin(YYINITIAL);
                              return HEREDOC_END;
                            }
                            return HEREDOC_CONTENT;
                          }
}
