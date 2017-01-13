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
1. In Atom, pull up the Settings tab by pressing <kbd>Ctrl</kbd>+<kbd>,</kbd>
   (<kbd>Cmd</kbd>+<kbd>,</kbd> on Mac) or using the main menu Edit -->
   Preferences
1. Click on the Install tab
1. Search for "parinfer" and find this package
1. Press the Install button :)

## Usage

### File Extensions

Once the package has been installed, it will automatically load in the
background when you open Atom and watch for file extensions found in a config
file. The default file extensions are [listed here].

You can edit these file extensions by going to Packages --> Parinfer --> Edit
File Extensions in the menu.

### Opening a File

When a file with a recognized extension is first opened, Parinfer runs [Paren
Mode] on the entire file and one of three things will happen (in order of
likelihood):

* **The file was unchanged.** You will be automatically dropped into Indent
  Mode. This is the most likely scenario once you start using Parinfer
  regularly.
* **Paren Mode changed the file.** You will be prompted to apply the changes
  (recommended) and then dropped into Indent Mode. This is most likely to happen
  when you first start using Parinfer on an existing file.
* **Paren Mode failed.** This is almost certainly caused by having unbalanced
  parens in your file (ie: it will not compile). A prompt will show and you will
  be dropped into Paren Mode in order to fix the problem.

Running Paren Mode is a necessary first step before Indent Mode can be safely
turned on. See [Fixing existing files] for more information.

If you do not want to be prompted when opening a new file, the prompts can be
disabled via [config file].

Please be aware that - depending on the indentation and formatting in your Lisp
files - this initial processing may result in a large diff the first time it
happens. Once you start using Indent Mode regularly, this initial processing is
unlikely to result in a large diff (or any diff at all). You may even discover
that applying Paren Mode to a file can result in [catching very hard-to-find
bugs] in your existing code! As usual, developers are responsible for reviewing
their diffs before a code commit :)

### Hotkeys and Status Bar

|  Command              | Windows/Linux                | Mac                         |
|-----------------------|-----------------------------:|-----------------------------|
| Turn on / Toggle Mode | <kbd>Ctrl</kbd>+<kbd>(</kbd> | <kbd>Cmd</kbd>+<kbd>(</kbd> |
| Turn off              | <kbd>Ctrl</kbd>+<kbd>)</kbd> | <kbd>Cmd</kbd>+<kbd>)</kbd> |

The status bar will indicate which mode you are in or show nothing if Parinfer
is turned off.

If you are in Paren Mode and Parinfer detects unbalanced parens (ie: code that
will not compile), the status bar text will be red. Note that this will never
happen in Indent Mode because Parinfer ensures that parens are always balanced.
Also note that there is a [known bug] with this feature due to the "parent
expression" hack explained below.

### Dim Trailing Parens

As of Atom 1.9, inferred closing parens are dimmed in Indent Mode:

```less
atom-text-editor.indent-mode-76f60::shadow {
  span.punctuation.section.end.trailing.clojure {
    opacity: 0.4; // <-- desired opacity of trailing parens
  }
}
```

Adding this feature to other language packages for Lisp can be done by porting
the [extra style classes that we added to language-clojure][style-extras].

[style-extras]:https://github.com/atom/language-clojure/pull/37/files

## Known Limitations

This extension uses a hack for performance reasons that may act oddly in certain
circumstances. It assumes that an open paren followed by an alpha character -
ie: regex `^\([a-zA-Z]` - at the start of a line is the beginning of a new
"parent expression" and tells the Parinfer algorithm to start analyzing from
there until the next line that matches the same regex. Most of the time this is
probably a correct assumption, but might break inside multi-line strings or
other non-standard circumstances. This is tracked at [Issue #9]; please add to
that if you experience problems.

## Future Features

Future features include:

* JSHint-like comments to automatically "turn on" Parinfer for files ([Issue #5](https://github.com/oakmac/atom-parinfer/issues/5))
* JSHint-like comments to tell Parinfer to ignore sections of your code ([Issue #6](https://github.com/oakmac/atom-parinfer/issues/6))
* A menu option to run Paren Mode on all files in a directory ([Issue #21](https://github.com/oakmac/atom-parinfer/issues/21))

More options and configuration settings are planned for future releases. Browse
the [issues] for an idea of future features. Create a new issue if you can think
of a useful feature :)

## Plugin Development Setup

```sh
# clone this repo to your homedir
cd ~
git clone https://github.com/oakmac/atom-parinfer.git

# symlink the repo to the Atom packages folder
ln -s ~/atom-parinfer ~/.atom/packages/

# compile CLJS files
lein cljsbuild auto
```

Then run Atom on a Lisp file.  Some development notes:

- `View > Developer > Reload Window` (to reload plugin changes)
- `View > Developer > Toggle Developer Tools` (to see console)

## License

[ISC License]

[here]:http://shaunlebron.github.io/parinfer/
[Parinfer]:http://shaunlebron.github.io/parinfer/
[Atom]:https://atom.io/
[listed here]:https://github.com/oakmac/atom-parinfer/blob/master/src-cljs/atom_parinfer/core.cljs#L67-L79
[issues]:https://github.com/oakmac/atom-parinfer/issues
[catching very hard-to-find bugs]:https://github.com/oakmac/atom-parinfer/commit/d4b49ec2636fd0530f3f2fbca9924db6c97d3a8f
[known bug]:https://github.com/oakmac/atom-parinfer/issues/32
[config file]:https://github.com/oakmac/atom-parinfer/issues/34#issuecomment-170146141
[Issue #9]:https://github.com/oakmac/atom-parinfer/issues/9
[Paren Mode]:http://shaunlebron.github.io/parinfer/#paren-mode
[Indent Mode]:http://shaunlebron.github.io/parinfer/#indent-mode
[Fixing existing files]:http://shaunlebron.github.io/parinfer/#fixing-existing-files
[ISC License]:LICENSE.md
