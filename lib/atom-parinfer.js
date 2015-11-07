var CompositeDisposable = require('atom').CompositeDisposable,
    subscriptions = null,
    fs = require('fs-plus');

var parinfer = require('./parinfer.js');

// TODO:
// - "run on this whole file" command option

//------------------------------------------------------------------------------
// Util
//------------------------------------------------------------------------------

function endsWith(train, caboose) {
  return train.substring(train.length - caboose.length) === caboose;
}

//------------------------------------------------------------------------------
// File Extensions to Watch
//------------------------------------------------------------------------------

// TODO: make this work cross-platform
const fileExtensionConfigFile = fs.getHomeDirectory() + '/.parinfer-file-extensions.txt';
const utf8 = 'utf8';
const defaultFileExtensions = ['.clj', '.cljs', '.cljc'];
const defaultFileExtensionConfigFile = '# one file extension per line please :)\n' +
                                       defaultFileExtensions.join('\n');
var fileExtensionsToWatch = defaultFileExtensions;

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

// a cache of the previously inferred text by editor.id
// prevents double-inferring the same text and causing an infinite loop
var lastInferredText = {};

function runParinfer(editor) {
  // safeguard; should never happen
  if (! editor) return;

  var editorId = editor.id.toString(),
    currentTxt = editor.getText(),
    cursorPosition = editor.getCursorBufferPosition(),
    allLines = currentTxt.split('\n'),
    startRow = findStartRow(allLines, cursorPosition.row),
    endRow = findEndRow(allLines, cursorPosition.row);

  // safeguard: should never happen
  if (! (startRow <= endRow)) return;

  // NOTE: would it be faster to to an array splice + join here?
  var textToInfer = '';
  for (var i = startRow; i < endRow; i++) {
    textToInfer += allLines[i] + '\n';
  }

  // do not process already-inferred text
  // NOTE: this check prevents an infinite loop of Atom doing
  //       setText --> getText --> setText --> getText, etc
  if (lastInferredText[editorId] === textToInfer) return;

  var inferredText = parinfer.formatText(textToInfer, cursorPosition);
  if (inferredText) {
    editor.setTextInBufferRange([[startRow, 0], [endRow, 0]], inferredText);
    editor.setCursorBufferPosition(cursorPosition);
    lastInferredText[editorId] = inferredText;
  }
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

//------------------------------------------------------------------------------
// Events
//------------------------------------------------------------------------------

function editFileExtensions() {
  // open the file extension config file in a new tab
  atom.workspace.open(fileExtensionConfigFile).then(function(editor) {
    // reload their file extensions when the editor is saved
    editor.onDidSave(function() {
      parseFileExtensionConfig(editor.getText());
    });
  });
}

// runs everytime a new editor window is opened
function newTextEditorOpened(editor) {
  var filePath = editor.getPath();

  if (typeof filePath !== 'string') return;

  for (var i = 0; i < fileExtensionsToWatch.length; i++) {
    if (endsWith(filePath, fileExtensionsToWatch[i])) {
      editor.onDidStopChanging(runParinfer.bind(null, editor));
      return; // early return
    }
  }
}

//------------------------------------------------------------------------------
// Atom-required Extension Functions
//------------------------------------------------------------------------------

function activate(_state) {
  console.log('Parinfer package activated');

  loadFileExtensionsToWatch();
  atom.workspace.observeTextEditors(newTextEditorOpened);

  subscriptions = new CompositeDisposable;
  subscriptions.add(atom.commands.add('atom-workspace', {
    'parinfer:editFileExtensions': editFileExtensions
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
