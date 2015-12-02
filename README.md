# Parinfer for Atom

A [Parinfer] package for [Atom].

## What is Parinfer?

Parinfer is a text editing mode that can infer Lisp code structure from
indentation (and vice versa). A detailed explanation of Parinfer can be found
[here].

Put simply: the goal of Parinfer is to make it so you never have to think about
"balancing your parens" when writing or editing Lisp code. Just indent your code
as normal and Parinfer will infer the intended paren structure.

## Installation

1. Install [Atom]
1. In Atom, pull up the Settings tab by pressing `Ctrl + ,` (`Cmd + ,` on Mac)
   or using the main menu Edit --> Preferences
1. Click on the Install tab
1. Search for "parinfer" and find this package
1. Press the Install button :)

## Usage

Once the package has been installed, it will automatically load in the
background when you open Atom and watch for file extensions found in a config
file. The default file extensions are: `.clj` `.cljs` `.cljc` `.lfe`

You can edit these file extensions by going to Packages --> Parinfer --> Edit
File Extensions in the menu.

When a file is first opened, Parinfer runs [Paren Mode] on the entire file and
then turns on [Indent Mode] if Paren Mode succeeded (ie: the file contained
balanced parens). See [Fixing existing files] for more information on why this
happens.

Please be aware that - depending on the indentation and formatting in your Lisp
files - this initial processing may result in a large diff the first time it
happens. Once you start using Indent Mode regularly, this initial processing is
unlikely to result in a large diff (or any diff at all). You may even discover
that applying Paren Mode to a file can result in [catching very hard-to-find
bugs] in your existing code! As usual, developers are responsible for reviewing
their diffs before a code commit :)

Use hotkey `Ctrl + (` to turn Parinfer on and to toggle between Indent Mode and
Paren Mode.

Use hotkey `Ctrl + )` to disable Pariner.

The status bar will indicate which mode you are in or show nothing if Parinfer
is turned off.

If you are in Paren Mode and Parinfer detects unbalanced parens (ie: code that
will not compile), the status bar text will be red. Note that this will never
happen in Indent Mode because Parinfer ensures that parens are always balanced.

More options and configuration settings are planned for future releases. Browse
the [issues] for an idea of future features. Create a new issue if you can think
of a useful feature :)

## Known Limitations

This extension uses a trick for performance reasons that may act oddly in
certain circumstances. It assumes that an open paren followed by a "word"
character - ie: regex `^\(\w` - at the start of a line is the start of a new
expression and tells the Parinfer algorithm to start analyzing from there until
the next line that matches the same regex. In 99% of cases, this is probably a
correct assumption, but might break inside multi-line strings or other
non-standard circumstances (hat-tip to [Shaun] for pointing out the multi-line
string case). This is tracked at [Issue #9]; please add to that if you
experience problems.

atom-parinfer keeps a small [LRU cache] of Indent Mode and Paren Mode results
for performance reasons. The size of the cache is small and unlikely to cause
problems on modern hardware. If you run into memory issues using atom-parinfer,
please open an issue.

Please take note: this is a new extension and Parinfer itself is very new.
Please report bugs and feature requests in the [issues].

## Future Features

Future features include:

* JSHint-like comments to automatically "turn on" Parinfer for files ([Issue #5](https://github.com/oakmac/atom-parinfer/issues/5))
* JSHint-like comments to tell Parinfer to ignore sections of your code ([Issue #6](https://github.com/oakmac/atom-parinfer/issues/6))
* Better UX before Paren Mode makes changes to a newly-opened file ([Issue #18](https://github.com/oakmac/atom-parinfer/issues/18))
* A menu option to run Paren Mode on all files in a directory ([Issue #21](https://github.com/oakmac/atom-parinfer/issues/21))

## License

[ISC License]

[here]:http://shaunlebron.github.io/parinfer/
[Parinfer]:http://shaunlebron.github.io/parinfer/
[Atom]:https://atom.io/
[issues]:https://github.com/oakmac/atom-parinfer/issues
[catching very hard-to-find bugs]:https://github.com/oakmac/atom-parinfer/commit/d4b49ec2636fd0530f3f2fbca9924db6c97d3a8f
[Shaun]:https://github.com/shaunlebron/
[Issue #9]:https://github.com/oakmac/atom-parinfer/issues/9
[Paren Mode]:http://shaunlebron.github.io/parinfer/#paren-mode
[Indent Mode]:http://shaunlebron.github.io/parinfer/#indent-mode
[Fixing existing files]:http://shaunlebron.github.io/parinfer/#fixing-existing-files
[LRU cache]:https://en.wikipedia.org/wiki/Cache_algorithms#LRU
[ISC License]:LICENSE.md
