## 1.24.0 - 2020-09-07
* Add configurable comment character. Thank you [@swlkr] for [PR #106]!
* Update to parinfer.js v3.13.0 (@oakmac fork)
* Change default for Smart Mode to "on". Remove "experimental" language.
* Bump project dependencies to latest

[@swlkr]:https://github.com/swlkr
[PR #106]:https://github.com/oakmac/atom-parinfer/pull/106

## 1.23.0 - 2018-07-03
* Remove `TextBuffer.setTextInRange` depreciation warning. [Issue #101]
* Bump ClojureScript version to 1.10.339

## 1.22.0 - 2018-02-04
* Update to parinfer.js v3.12.0 [PR #99]

## 1.21.0 - 2018-01-28
* Fix for Atom v1.23.3 [PR #97]

## 1.20.0 - 2017-08-08
* Do not require restart when switching between Indent and Smart mode.

## 1.19.1-3 - 2017-08-08
* Remove `TextEditor.editorElement` depreciation warning. [Issue #92]

## 1.19.0 - 2017-08-07
* Upgrade to parinfer.js v3.10.0
* Use Atom Config instead of files in the user's home directory.
* Add support for `smartMode`, `tabStops`, and error markers (thanks [@shaunlebron]!)

[@shaunlebron]:https://github.com/shaunlebron

## 1.18.0 - 2017-06-03
* Use Google Closure Advanced Mode optimizations. Significantly reduce plugin file size.

## 1.17.0 - 2017-01-12
* Update deprecated CSS selectors. [Issue #73] and [Issue #74]

## 1.16.0 - 2016-08-14
* Bump to parinfer.js v1.8.1
* Only dim inferred parens in Indent Mode. [Issue #65]
* Add `previewCursorScope` option (default is off). [Issue #67]

## 1.15.0 - 2016-06-12
* Add CSS for dimming trailing parens. [PR #59]
* Fix issue with loading file extensions on startup. [Issue #60]

## 1.14.0 - 2016-02-21
* Correctly apply the cursor result from Parinfer.

## 1.13.0 - 2016-02-21
* Fix issue with multi-cursor editing. [Issue #49]

## 1.12.1 - 2016-02-21
* Upgrade to ClojureScript 1.7.228
* Replace `rodnaph/lowline` with Google Closure functions

## 1.12.0 - 2016-02-21
* Upgrade to Parinfer v1.6.1
* Increase debounce interval to 20ms

## 1.11.0 - 2016-02-12
* Change key bindings to use <kbd>Cmd</kbd> on Mac

## 1.10.0 - 2016-02-04
* Upgrade to Parinfer v1.5.3
* Add `.el` (Emacs Lisp) as a default file extension
* Remove the LRU cache
* Fix a bug with the parent expression hack. [Issue #35]

## 1.9.0 - 2016-02-02
* Upgrade to Parinfer v1.5.2

## 1.8.0 - 2016-01-25
* Upgrade to Parinfer v1.5.1

## 1.7.0 - 2016-01-11
* Add new `split-lines` function. [PR #43]

## 1.6.0 - 2016-01-11
* Fix index out of range vector error. [Issue #42]

## 1.5.0 - 2016-01-09
* Fix a bug with CRLF lines. [Issue #37]

## 1.4.0 - 2016-01-08
* Allow user to skip the open file dialogs via config flag. [Issue #34]

## 1.3.0 - 2016-01-06
* Upgrade to Parinfer v1.4.0

## 1.0.0 - 2015-12-23
* Upgrade to the new JavaScript version of Parinfer. Everything should be much faster now.

## 0.10.0 - 2015-12-19
* Prompt the user when first opening a file if Paren Mode didn't succeed or
  needs to make changes to the file. Issues [#18] and [#24].

## 0.6.0 - 2015-11-13
* Re-write of atom-parinfer in ClojureScript

## 0.3.0 - 2015-11-08
* Correctly watch the cursor for changes
* Cleaner event triggering

## 0.2.0 - 2015-11-08
* Initial release!
* Parinfer now usable in a major text editor. One small step for parenthesis,
  one giant leap for Lisp ;)

[#18]:https://github.com/oakmac/atom-parinfer/issues/18
[#24]:https://github.com/oakmac/atom-parinfer/issues/24
[Issue #34]:https://github.com/oakmac/atom-parinfer/issues/34
[Issue #37]:https://github.com/oakmac/atom-parinfer/issues/37
[Issue #42]:https://github.com/oakmac/atom-parinfer/issues/42
[Issue #35]:https://github.com/oakmac/atom-parinfer/issues/35
[Issue #49]:https://github.com/oakmac/atom-parinfer/issues/49
[Issue #60]:https://github.com/oakmac/atom-parinfer/issues/60
[Issue #65]:https://github.com/oakmac/atom-parinfer/issues/65
[Issue #67]:https://github.com/oakmac/atom-parinfer/issues/67
[Issue #73]:https://github.com/oakmac/atom-parinfer/issues/73
[Issue #74]:https://github.com/oakmac/atom-parinfer/issues/74
[Issue #92]:https://github.com/oakmac/atom-parinfer/issues/92
[Issue #101]:https://github.com/oakmac/atom-parinfer/issues/101
[PR #43]:https://github.com/oakmac/atom-parinfer/pull/43
[PR #59]:https://github.com/oakmac/atom-parinfer/pull/59
[PR #97]:https://github.com/oakmac/atom-parinfer/pull/97
[PR #99]:https://github.com/oakmac/atom-parinfer/pull/99
