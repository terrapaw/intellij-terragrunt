# Architecture

## Lexer Design: Multi-Token Strings

The lexer emits multiple tokens per quoted string to support `${...}` interpolation natively in the PSI tree.

A string like `"hello ${name}"` is lexed as:

```
QUOTE  STRING_LITERAL("hello ")  INTERPOLATION_START  IDENTIFIER("name")  INTERPOLATION_END  QUOTE
```

A simple string like `"foo"` is 3 tokens: `QUOTE`, `STRING_LITERAL("foo")`, `QUOTE`.
An empty string `""` is 2 tokens: `QUOTE`, `QUOTE`.

### Why

This gives full PSI inside interpolations — navigation, completion, references, and highlighting all work automatically inside `${...}` without needing language injection or secondary parsing.

The `QUOTE` token type distinguishes string delimiters from content, allowing the grammar to properly terminate strings. Without it, consecutive quoted strings (e.g. object keys on separate lines) would be parsed as one long string.

### Tradeoff

The alternative (used by the JetBrains Terraform/HCL plugin) is to emit the entire `"..."` as a single token and handle interpolation at a higher level via string manipulation or language injection. That's simpler for structural parsing but requires extra infrastructure for IDE features inside strings.

### Consequences

- Grammar: `string_lit ::= QUOTE (STRING_LITERAL | interpolation)* QUOTE`
- Grammar: `label ::= (QUOTE STRING_LITERAL* QUOTE)+ | IDENTIFIER`
- `"vpc" "extra"` (two HCL labels) becomes one label node with 6 tokens (QUOTE STRING_LITERAL QUOTE QUOTE STRING_LITERAL QUOTE). The `TerragruntLabelCountInspection` uses quote-counting to detect this.
- String value extraction always needs a helper (`extractStringContent()`) to strip quotes.

### Lexer States

```
YYINITIAL → STRING (on ")       → INTERPOLATION (on ${) → back to STRING (on })
YYINITIAL → HEREDOC_ID          → HEREDOC_BODY (with interpolation support)
YYINITIAL → BLOCK_COMMENT_STATE
```

Nested strings inside interpolation are supported: `"${func("inner")}"` — the lexer pushes a new STRING state inside INTERPOLATION.

## Grammar Design

Follows the [HCL spec](https://github.com/hashicorp/hcl/blob/main/hclsyntax/spec.md):

```
Body      = (Attribute | Block)*
Attribute = Identifier "=" Expression
Block     = Identifier (StringLit|Identifier)* "{" Body "}"
```

### Block vs Attribute Disambiguation

```
body_item ::= block | attribute {recoverWhile=body_item_recover}
block     ::= IDENTIFIER label* LBRACE body RBRACE {pin=3}
attribute ::= IDENTIFIER EQUALS expression {pin=2}
```

- `attribute` pins on `=` (2nd element) — commits once `=` is seen.
- `block` pins on `{` (3rd element) — commits once `{` is seen.
- Parser tries `block` first. If the token after IDENTIFIER isn't a label or `{`, it falls through to `attribute`.
- `recoverWhile` on `body_item` skips tokens until the next IDENTIFIER or `}` on parse errors.

### Expression Precedence

Expressions use a precedence chain (lowest to highest):

```
expression → conditional → or → and → equality → comparison → additive → multiplicative → unary → postfix → primary
```

`primary` includes: function calls, variable references, literals, collection values, for-expressions, parenthesized expressions, and template expressions (strings/heredocs).
