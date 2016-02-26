# Clojure Grammar for Parinfer

This is a modification to [language-clojure 0.19.1] which adds the
`.paren-trail` class to Paren Trail tokens.

[language-clojure 0.19.1]:https://github.com/atom/language-clojure/blob/master/grammars/clojure.cson

Gif showing Before and After `.paren-trail { opacity: 0.3; }`:

![gif](http://i.imgur.com/gLlW7fB.gif)

## How are Paren Trails detected?

The original Clojure grammar has regex patterns for detecting where the
collection delimiters begin and end.  For example, here's the original regex
pattern for detecting the end of a map `}`.

```coffee
'end': '(\\})'
'endCaptures':
  '1':
    'name': 'punctuation.section.map.end.clojure'
```

Since the `end` regex can have capture groups, `endCaptures` allows you to
apply a CSS class to each group by index.

We want to create a regex that will detect if the `}` is in the Paren Trail,
then apply the `.paren-trail` CSS class.  We perform the following
modification, with our regex exploded below in detail:

```coffee
'end': '(\\}(?=[\\}\\]\\)\\s]*(?:;|\\n)))|(\\})'

#       regex exploded...
#       ---------------------------------------
#       (                               )|(   )  <----- result is 1 of 2 possible capture groups
#        \\}                               \\}   <----- both start with a `}`
#           (?=                        )         <----- 1st capture group has a lookahead (which is not captured)
#              [            ]*                   <----- zero or more characters that are...
#               \\}\\]\\)\\s                     <----- ... `}` `]` `)` or whitespace
#                             (?:     )          <----- end with a non-captured group...
#                                ;|\\n           <----- ... a semicolon or newline
#       ---------------------------------------

'endCaptures':
  '1':
    'name': 'punctuation.section.map.end.clojure.paren-trail'
  '2':
    'name': 'punctuation.section.map.end.clojure'
```

  We apply these modifications for each of the following patterns:

```
  {} `map`
 ^{} `meta.metadata.map.clojure`
 #{} `set`
  [] `vector`
```

## Special Case for S-Expressions

To apply modifications to the following patterns, we must do something slightly
different.

```
  () `sexp`
 '() `quoted-sexp`
 `() `quoted-sexp` (backtick-quoted)
```

The original grammars for the patterns above will treat `)\n` specially, by
adding a `after-expression` class to the `\n` token.  See grammar below:

```coffee
'end': '(\\))(\\n)?'
'endCaptures':
  '1':
    'name': 'punctuation.section.expression.end.clojure'
  '2':
    'name': 'meta.after-expression.clojure'
```


```coffee
'end': '(\\))(\\n)|(\\)(?=[\\}\\]\\)\\s]*(?:;|\\n)))|(\\))'

#       regex exploded...
#       --------------------------------------------------
#       (   )(   )|(                               )|(   )  <----- high-level capture groups (4)
#        \\)  \\n                                           <----- 1st and 2nd groups are together
#                   ...............................         <----- 3rd group same as 1st in previous section
#                                                     \\)   <----- 4th group
#       --------------------------------------------------

'endCaptures':
  '1':
    'name': 'punctuation.section.expression.end.clojure.paren-trail'
  '2':
    'name': 'meta.after-expression.clojure'
  '3':
    'name': 'punctuation.section.expression.end.clojure.paren-trail'
  '4':
    'name': 'punctuation.section.expression.end.clojure'
```

## Resources

Atom's Grammar files are the same as TextMate's: <https://manual.macromates.com/en/language_grammars>


