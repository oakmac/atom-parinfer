//!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
//!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
//
// 13 Nov 2015 - C. Oakman
//
// This is the original atom-parinfer implementation written in JS; now
// re-written in ClojureScript in the src-cljs/atom-parinfer/core.cljs file.
//
// Keeping this file in the repo for historial purposes as well as a possible
// future reference.
//
//!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
//!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

// requires
var CompositeDisposable = require('atom').CompositeDisposable,
    fs = require('fs-plus'),
    _ = require('underscore'),
    parinfer = require('./parinfer.js');

// TODO: this isn't really ready yet; needs to be configurable
// leave it out for now
var notificationsEnabled = false;

// stateful
var editorStates = {}, // keep track of the Parinfer mode for each editor instance
    fileExtensionsToWatch = null, // array of file extensions to watch and automatically start parinfer
    subscriptions = null; // stupid variable for Atom's stupid CompositeDisposable system

// NOTE: This value seems to work well for the debounce interval.
// I don't notice any lag when typing on my machine and the result seems to
// display fast enough that it doesn't feel "delayed".
// Feel free to play around with it on your machine if that is not the case.
const debounceIntervalMs = 10;

// editor states
const DISABLED = 'disabled',
      INDENT_MODE = 'indent-mode',
      PAREN_MODE = 'paren-mode';

//------------------------------------------------------------------------------
// Util
//------------------------------------------------------------------------------

function endsWith(train, caboose) {
  return train.substring(train.length - caboose.length) === caboose;
}

// http://stackoverflow.com/questions/105034/create-guid-uuid-in-javascript#answer-2117523
function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
    var r = Math.random()*16|0, v = c == 'x' ? r : (r&0x3|0x8);
    return v.toString(16);
  });
}

function byId(id) {
  return document.getElementById(id);
}

function qs(selector) {
  return document.querySelector(selector);
}

//------------------------------------------------------------------------------
// File Extensions to Watch
//------------------------------------------------------------------------------

// TODO: make this work cross-platform
const fileExtensionConfigFile = fs.getHomeDirectory() + '/.parinfer-file-extensions.txt',
      utf8 = 'utf8',
      defaultFileExtensions = ['.clj', '.cljs', '.cljc'],
      defaultFileExtensionConfigFile = '# one file extension per line please :)\n' +
                                       defaultFileExtensions.join('\n');

fileExtensionsToWatch = defaultFileExtensions;

// TODO: break this into two functions:
//       one to parse the text, the other to set fileExtensionsToWatch
function parseFileExtensionConfig(txt) {
  // clear out the file extensions to watch
  fileExtensionsToWatch = [];

  var lines = txt.split('\n');
  lines.forEach(function(l) {
    // trim the line
    l = l.trim();

    // skip comments
    if (l.charAt(0) === '#') return;

    // skip empty lines
    if (l === '') return;

    fileExtensionsToWatch.push(l);
  });
}

function createDefaultFileExtensionConfig(successFn) {
  fs.writeFile(fileExtensionConfigFile, defaultFileExtensionConfigFile, function(err) {
    if (err) {
      // TODO: handle error here
    }
    else {
      successFn();
    }
  });
}

function loadFileExtensionsToWatch() {
  fs.readFile(fileExtensionConfigFile, utf8, function(err, txt) {
    if (err) {
      createDefaultFileExtensionConfig(parseFileExtensionConfig.bind(null, defaultFileExtensionConfigFile));
    }
    else {
      parseFileExtensionConfig(txt);
    }
  });
}

//------------------------------------------------------------------------------
// Run Parinfer on the TextEditor instance
//------------------------------------------------------------------------------

function runParinfer(editor, changeEvt) {
  // safeguard; should never happen
  if (! editor) return;

  // do not run parinfer if autocomplete is showing
  if (isAutocompleteShowing() === true) return;

  // do nothing if parinfer is disabled
  var editorId = editor.id.toString();
  if (editorStates[editorId] === DISABLED) return;

  var currentTxt = editor.getText(),
      cursorPosition = editor.getCursorBufferPosition(),
      selections = editor.getSelectedBufferRanges(),
      allLines = currentTxt.split('\n');

  // add a newline at the end of the file if there is not one
  // https://github.com/oakmac/atom-parinfer/issues/12
  if (allLines[allLines.length - 1] !== '') {
    allLines.push('');
  }

  var startRow = findStartRow(allLines, cursorPosition.row),
      endRow = findEndRow(allLines, cursorPosition.row),
      // adjust the cursor position for the section we are sending to Parinfer
      adjustedCursorPosition = {row: (cursorPosition.row - startRow),
                                column: cursorPosition.column};

  // safeguard; should never happen
  if (! (startRow <= endRow)) return;

  // NOTE: would it be faster to to an array splice + join here?
  var textToInfer = '';
  for (var i = startRow; i < endRow; i++) {
    textToInfer += allLines[i] + '\n';
  }

  // run the parinfer algorithm
  var inferredText;
  if (editorStates[editorId] === INDENT_MODE) {
    inferredText = parinfer.indentMode(textToInfer, adjustedCursorPosition);
  }
  else if (editorStates[editorId] === PAREN_MODE) {
    inferredText = parinfer.parenMode(textToInfer, adjustedCursorPosition);
  }

  // do not update the editor if parinfer did not run successfully
  if (typeof inferredText !== 'string') return;

  // do not update the editor if the text has not changed
  if (textToInfer === inferredText) return;

  // update the editor
  editor.setTextInBufferRange([[startRow, 0], [endRow, 0]], inferredText, {undo: 'skip'});
  editor.setCursorBufferPosition(cursorPosition);
  editor.setSelectedBufferRanges(selections);
}

// returns the index of the first line we need to send to parinfer
function findStartRow(allLines, cursorLineIdx) {
  // on the first line?
  if (cursorLineIdx === 0) return 0;

  // is the cursor on the first row of a parent expression?
  // if so, we need to include the parent expression above it as the user may
  // have just pressed "Enter" to get to this line
  if (isParentExpressionLine(allLines[cursorLineIdx]) === true) {
    cursorLineIdx--;
  }

  // "look up" until we find the closest parent expression
  for (var i = cursorLineIdx; i >= 0; i--) {
    if (isParentExpressionLine(allLines[i]) === true) {
      return i;
    }
  }

  // return the beginning of the file if we didn't find anything
  return 0;
}

// returns the index of the last line we need to send to parinfer
function findEndRow(allLines, cursorLineIdx) {
  // "look down" until we find the start of the next parent expression
  var len = allLines.length;
  for (var i = (cursorLineIdx + 1); i < len; i++) {
    if (isParentExpressionLine(allLines[i]) === true) {
      return i;
    }
  }

  // return the end of the file if we didn't find anything
  return len - 1;
}

function isParentExpressionLine(line) {
  return line.charAt(0) === '(';
}

// queries the DOM to determine if the autocomplete overlay is showing
// TODO: is there a cleaner or faster way to check this?
const autocompleteElSelector = 'atom-text-editor.is-focused.autocomplete-active';
function isAutocompleteShowing() {
  return !!document.querySelector(autocompleteElSelector);
}

//------------------------------------------------------------------------------
// Status Bar
//------------------------------------------------------------------------------

const statusElId = uuid();

function statusBarLink(state) {
  var txt;
  if (state === INDENT_MODE) { txt = 'Parinfer: Indent'; }
  if (state === PAREN_MODE)  { txt = 'Parinfer: Paren'; }

  return '<a class="inline-block">' + txt + '</a>';
}

function addStatusElToDom() {
  var parentEl = qs('status-bar div.status-bar-right');

  // exit if there is no status bar
  if (! parentEl) return;

  var statusBarEl = document.createElement('div');
  statusBarEl.className = 'inline-block parinfer-notification-c7a5b';
  statusBarEl.id = statusElId;

  parentEl.insertBefore(statusBarEl, parentEl.firstChild);
}

function updateStatusBar(newState) {
  // remove the status bar if Parinfer is disabled
  if (newState === DISABLED) {
    removeStatusBar();
    return; // early return
  }

  // inject the status bar element if necessary
  var statusEl = byId(statusElId);
  if (! statusEl) {
    addStatusElToDom();
    statusEl = byId(statusElId);
  }

  if (statusEl) {
    statusEl.innerHTML = statusBarLink(newState);
  }
  // HACK: sometimes it takes Atom a while to boot up the DOM, so we keep trying
  // until the DOM exists
  else {
    setTimeout(updateStatusBar.bind(null, newState), 100);
  }
}

function removeStatusBar() {
  var statusBarEl = byId(statusElId);
  if (statusBarEl) {
    statusBarEl.parentNode.removeChild(statusBarEl);
  }
}

//------------------------------------------------------------------------------
// Events
//------------------------------------------------------------------------------

function panelChanged() {
  var editor = atom.workspace.getActiveTextEditor();

  // clear the status bar if this is an editor that is not running parinfer
  if (! editor || editorStates.hasOwnProperty(editor.id.toString()) !== true) {
    removeStatusBar();
  }
  // else update it
  else {
    var editorId = editor.id.toString();
    updateStatusBar(editorStates[editorId]);
  }
}


function disable() {
  var editor = atom.workspace.getActiveTextEditor();

  // exit if there is no editor
  if (! editor) return;

  var editorId = editor.id.toString();

  // set the state
  editorStates[editorId] = DISABLED;
  if (notificationsEnabled === true) {
    atom.notifications.addInfo('Parinfer: Disabled', {detail: 'Press Ctrl + 9 to turn Parinfer back on'});
  }

  updateStatusBar(DISABLED);
}

function toggleMode() {
  var editor = atom.workspace.getActiveTextEditor();

  // exit if there is no editor
  if (! editor) return;

  var editorId = editor.id.toString(),
      currentState = editorStates[editorId];

  // set the state
  // NOTE: got a nice little state machine going here :)
  if (! currentState || currentState === DISABLED || currentState === PAREN_MODE) {
    editorStates[editorId] = INDENT_MODE;
    if (notificationsEnabled === true) {
      atom.notifications.addInfo('Parinfer: Indent Mode on', {detail: 'Parens determined by indentation.'});
    }
    updateStatusBar(INDENT_MODE);
  }
  else if (currentState === INDENT_MODE) {
    editorStates[editorId] = PAREN_MODE;
    if (notificationsEnabled === true) {
      atom.notifications.addInfo('Parinfer: Paren Mode on', {detail: 'Indentation determined by parens.'});
    }
    updateStatusBar(PAREN_MODE);
  }
}

function editFileExtensions() {
  // open the file extension config file in a new tab
  atom.workspace.open(fileExtensionConfigFile).then(function(editor) {
    // reload their file extensions when the editor is saved
    editor.onDidSave(function() {
      parseFileExtensionConfig(editor.getText());
    });
  });
}

// runs when the editor is destroyed
// TODO: I'm not even sure this is necessary?
function goodbyeEditor(editor) {
  if (editor && editor.id) {
    delete editorStates[editor.id.toString()];
  }
}

// runs when a new editor window is opened
function helloEditor(editor) {
  // TODO: add parinfer events to the editor object
  var editorId = editor.id.toString();


  // TODO: run parinfer init if we recognize the file extension
}

// runs everytime a new editor window is opened
function helloEditor(editor) {
  // make sure it matches the file extensions we are watching
  var filePath = editor.getPath();
  if (fileMatchesWatchedExtensions(filePath) !== true) return;

  // run setIndentation when the file is first opened
  var currentText = editor.getText();
      indentedText = parinfer.parenMode(currentText);
  if (typeof indentedText === 'string') {
    editor.setText(indentedText);
  }

  // drop them into Indent Mode
  editorStates[editor.id.toString()] = INDENT_MODE;
  updateStatusBar(INDENT_MODE);

  var runBoundToEditor = runParinfer.bind(null, editor),
      debouncedRun = _.debounce(runBoundToEditor, debounceIntervalMs);

  // add the event on any cursor or text change
  editor.onDidStopChanging(debouncedRun);
  editor.onDidChangeCursorPosition(debouncedRun);

  // add the destroy event
  editor.onDidDestroy(goodbyeEditor);
}

function fileMatchesWatchedExtensions(filename) {
  if (typeof filename !== 'string') return false;

  for (var i = 0; i < fileExtensionsToWatch.length; i++) {
    if (endsWith(filename, fileExtensionsToWatch[i])) {
      return true;
    }
  }

  return false;
}

//------------------------------------------------------------------------------
// Atom-required Extension Functions
//------------------------------------------------------------------------------

function activate(_state) {
  console.log('Parinfer package activated');

  loadFileExtensionsToWatch();
  atom.workspace.observeTextEditors(helloEditor);
  atom.workspace.onDidChangeActivePaneItem(panelChanged);

  subscriptions = new CompositeDisposable;
  subscriptions.add(atom.commands.add('atom-workspace', {
    'parinfer:editFileExtensions': editFileExtensions,
    'parinfer:disable': disable,
    'parinfer:toggleMode': toggleMode
  }));
}

function deactivate() {
  subscriptions.dispose();
}

function serialize() {
  // noop
}

//------------------------------------------------------------------------------
// Module Exports
//------------------------------------------------------------------------------

module.exports = {
  activate: activate,
  deactivate: deactivate,
  serialize: serialize
};
