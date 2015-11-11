# Parinfer for Atom

A [Parinfer] package for [Atom].

## What is Parinfer?

Parinfer is a new idea to infer LISP code structure from indentation. Detailed
information about Parinfer can be found [here].

This repo is an Atom package that implements the main Parinfer algorithm as you
type. In short, the goal of this package is to make it so that you never have to
think about "balancing your parens"; just indent your code as normal and
Parinfer will take care of the rest.

## How to use this Extension

1. Install [Atom]
1. In Atom, pull up the Settings tab by pressing `Ctrl + ,` or using the main
   menu Edit --> Preferences
1. Click on the Install tab
1. Search for "parinfer" and find this package
1. Press the Install button :)

Once the package has been installed, it will automatically load in the
background when you open Atom and watch for file extensions found in a config
file. The default file extensions are: `.clj` `.cljs` `.cljc`

You can edit the file extension config by going to Packages --> Parinfer -->
Edit File Extensions in the menu.

More options and configuration settings are planned for future releases. Browse
the [issues] for an idea of future features. Create a new issue if you can think
of a useful feature :)

## Known Limitations

This extension uses a trick for performance reasons that may act oddly in
certain circumstances. It assumes that an open paren - ie: `(` - on the first
character of a line is the start of a new expression and tells the Parinfer
algorithm to start analyzing from there. In 99% of cases, this is probably a
correct assumption, but might break inside multi-line strings or other
non-standard circumstances (hat-tip to [Shaun] for pointing out the multi-line
string case). This is tracked at [Issue #9]; please add to that if you
experience problems.

Please take note: this is a brand new extension and Parinfer itself is very new.
Please report bugs and feature requests in the [issues].

## Future Features

Future features include:

* Status bar notification when Parinfer is on / off ([Issue #2](https://github.com/oakmac/atom-parinfer/issues/2))
* Toggle Parinfer on/off ([Issue #3](https://github.com/oakmac/atom-parinfer/issues/3))
* JSHint-like comments to automatically "turn on" Parinfer for files ([Issue #5](https://github.com/oakmac/atom-parinfer/issues/5))
* JSHint-like comments to tell Parinfer to ignore sections of your code ([Issue #6](https://github.com/oakmac/atom-parinfer/issues/6))

## License

[ISC License]

[here]:http://shaunlebron.github.io/parinfer/
[Parinfer]:http://shaunlebron.github.io/parinfer/
[Atom]:https://atom.io/
[issues]:https://github.com/oakmac/atom-parinfer/issues
[Shaun]:https://github.com/shaunlebron/
[Issue #9]:https://github.com/oakmac/atom-parinfer/issues/9
[Paren Mode]:http://shaunlebron.github.io/parinfer/#paren-mode
[ISC License]:LICENSE.md
