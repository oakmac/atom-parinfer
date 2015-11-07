var CompositeDisposable = require('atom').CompositeDisposable,
    subscriptions = null,
    fs = require('fs-plus');

var parinfer = require('./parinfer.js');

// TODO:
// - "run on this whole file" command option
// - "look up" to the previous parent expression

//------------------------------------------------------------------------------
// Util
//------------------------------------------------------------------------------

function endsWith(haystack, needle) {
  return haystack.substring(haystack.length - needle.length) === needle;
}

//------------------------------------------------------------------------------
// File Extensions to Watch
//------------------------------------------------------------------------------

// TODO: make this work cross-platform
const fileExtensionConfigFile = fs.getHomeDirectory() + '/.parinfer-file-extensions.txt';
const utf8 = 'utf8';
const defaultFileExtensions = ['.clj', '.cljs', '.cljc'];
const defaultFileExtensionConfigFile = '# one file extension per line please :)\n' + defaultFileExtensions.join('\n');
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

var LAST_INFERRED_TXT = null;

function banana(editor) {
  // safeguard; should never happen
  if (! editor) return;

  var currentTxt = editor.getText(),
    cursorPosition = editor.getCursorBufferPosition(),
    allLines = currentTxt.split('\n'),
    startRow = findParentExpStart(allLines, cursorPosition.row),
    endRow = findParentExpEnd(allLines, cursorPosition.row);

  // safeguard: should never happen
  if (! (startRow <= endRow)) return;

  var textToInfer = '';
  for (var i = startRow; i < endRow; i++) {
    textToInfer += allLines[i] + '\n';
  }

  // do not process already-inferred text
  // NOTE: this check prevents an infinite loop of Atom doing
  //       setText --> getText --> setText --> getText, etc
  if (LAST_INFERRED_TXT === textToInfer) return;

  var inferredText = parinfer.parinfer.formatText(textToInfer, cursorPosition);
  if (inferredText) {
    editor.setTextInBufferRange([[startRow, 0], [endRow, 0]], inferredText);
    editor.setCursorBufferPosition(cursorPosition);
    LAST_INFERRED_TXT = inferredText;
  }
}

function findParentExpStart(lines, cursorRowIdx) {
  for (var i = cursorRowIdx; i >= 0; i--) {
    if (isStartOfParentExpression(lines[i]) === true) {
      return i;
    }
  }

  return 0;
}

function findParentExpEnd(lines, cursorRowIdx) {
  var len = lines.length;
  for (var i = (cursorRowIdx + 1); i < len; i++) {
    if (isStartOfParentExpression(lines[i]) === true) {
      return i;
    }
  }

  return len - 1;
}

function isStartOfParentExpression(s) {
  return s.substring(0, 1) === '(';
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
  for (var i = 0; i < fileExtensionsToWatch.length; i++) {
    if (endsWith(filePath, fileExtensionsToWatch[i])) {
      editor.onDidStopChanging(banana.bind(null, editor));
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
