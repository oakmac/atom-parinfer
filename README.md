# Parinfer for Atom

A [Parinfer] package for [Atom].

[Parinfer]:http://shaunlebron.github.io/parinfer/
[Atom]:https://atom.io/

## What is Parinfer?

Parinfer is a text editing mode that can infer Lisp code structure from
indentation (and vice versa). A detailed explanation of Parinfer can be found
[here].

Put simply: the goal of Parinfer is to make it so you never have to think about
"balancing your parens" when writing or editing Lisp code. Just indent your code
as normal and Parinfer will infer the intended paren structure.

[here]:http://shaunlebron.github.io/parinfer/

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
background when you open Atom and watch for file extensions of popular Lisp
languages. The file extensions are [listed here] and can be changed in the
Settings.

[listed here]:https://github.com/oakmac/atom-parinfer/blob/b167244ea38b35c2d9a00dba76833f06a9bf7367/src-cljs/atom_parinfer/core.cljs#L30-L42

### Opening a File

When a file with a recognized extension is first opened, Parinfer runs [Paren
Mode] on the entire file and one of three things will happen (in order of
likelihood):

* **The file was unchanged.** You will be automatically dropped into [Indent
  Mode]. This is the most common scenario once you start using Parinfer
  regularly.
* **Paren Mode changed the file.** You will be prompted to apply the changes
  (recommended) and then dropped into Indent Mode. This is common the first time
  you use Parinfer on a file.
* **Paren Mode failed.** This scenario is uncommon and almost certainly means
  you have unbalanced parens in your file (ie: it will not compile). A prompt
  will show and you will be dropped into Paren Mode in order to fix the syntax
  problem.

Running Paren Mode is a necessary first step before Indent Mode can be safely
turned on. See [Fixing existing files] for more information.

If you do not want to be prompted when opening a new file, the prompts can be
disabled in the Settings.

Please be aware that - depending on the indentation and formatting in your Lisp
files - this initial processing may result in a large diff the first time it
happens. Once you start using Indent Mode regularly, this initial processing is
unlikely to result in a large diff (or any diff at all). You may even discover
that applying Paren Mode to a file can result in [catching very hard-to-find
bugs] in your existing code! As usual, developers are responsible for reviewing
their diffs before a code commit :)

If you want to convert a project over to Parinfer-compatible indentation, please
check out the [Parlinter] project.

[Paren Mode]:http://shaunlebron.github.io/parinfer/#paren-mode
[Indent Mode]:http://shaunlebron.github.io/parinfer/#indent-mode
[Fixing existing files]:http://shaunlebron.github.io/parinfer/#fixing-existing-files
[catching very hard-to-find bugs]:https://github.com/oakmac/atom-parinfer/commit/d4b49ec2636fd0530f3f2fbca9924db6c97d3a8f
[Parlinter]:https://github.com/shaunlebron/parlinter

### Hotkeys and Status Bar

|  Command              | Windows/Linux                                 | Mac                                          |
|-----------------------|----------------------------------------------:|----------------------------------------------|
| Turn on / Toggle Mode | <kbd>Ctrl</kbd>+<kbd>Shift</kbd>+<kbd>(</kbd> | <kbd>Cmd</kbd>+<kbd>Shift</kbd>+<kbd>(</kbd> |
| Turn off              | <kbd>Ctrl</kbd>+<kbd>Shift</kbd>+<kbd>)</kbd> | <kbd>Cmd</kbd>+<kbd>Shift</kbd>+<kbd>)</kbd> |

The status bar will indicate which mode you are in or show nothing if Parinfer
is turned off.

If you are in Paren Mode and Parinfer detects unbalanced parens (ie: code that
will not compile), the status bar text will be red. Note that this will never
happen in Indent Mode because Parinfer ensures that parens are always balanced.
Also note that there is a [known bug] with this feature due to the "parent
expression" hack explained below.

[known bug]:https://github.com/oakmac/atom-parinfer/issues/32

## Known Limitations

This extension uses a hack for performance reasons that may act oddly in certain
circumstances. It assumes that an open paren followed by an alpha character -
ie: regex `^\([a-zA-Z]` - at the start of a line is the beginning of a new
"parent expression" and tells the Parinfer algorithm to start analyzing from
there until the next line that matches the same regex. Most of the time this is
probably a correct assumption, but might break inside multi-line strings or
other non-standard circumstances. This is tracked at [Issue #9]; please add to
that if you experience problems.

Interestingly, [Shaun] discovered that this hack is not new. Someone else used
the same approach [36 years ago] :)

[Issue #9]:https://github.com/oakmac/atom-parinfer/issues/9
[Shaun]:https://github.com/shaunlebron/
[36 years ago]:images/zwei-top-level-expression-hack.png

## Plugin Development Setup

Install [Leiningen].

```sh
# clone this repo to your homedir
cd ~
git clone https://github.com/oakmac/atom-parinfer.git

# symlink the repo to the Atom packages folder
ln -s ~/atom-parinfer ~/.atom/packages/parinfer

# compile CLJS files
lein cljsbuild auto
```

Then run Atom on a Lisp file.  Some development notes:

- `View > Developer > Reload Window` (to reload plugin changes)
- `View > Developer > Toggle Developer Tools` (to see console)

[Leiningen]:http://leiningen.org

## License

[ISC License](LICENSE.md)
