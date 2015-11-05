var CompositeDisposable = require('atom').CompositeDisposable,
    subscriptions = null,
    ENABLED = false;

var parinfer = require('./parinfer.js');

// TODO: need a "run on this whole file" command option

//------------------------------------------------------------------------------
// Run Parinfer on the TextEditor instance
//------------------------------------------------------------------------------

var LAST_INFERRED_TXT = null;

function banana(x) {
  var editor = atom.workspace.getActiveTextEditor();
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

  // do not process twice
  if (LAST_INFERRED_TXT === textToInfer) return;

  var inferredText = parinfer.parinfer.formatText(textToInfer, cursorPosition);

  // HACK: Shaun says he is going to fix this in parinfer
  if (textToInfer.substring(textToInfer.length - 1) === '\n' &&
      (startRow + 1) !== endRow) {
    inferredText += '\n';
  }

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
// Atom-required Export Functions
//------------------------------------------------------------------------------

// enables parinfer
function enable() {
  console.log('Parinfer is ON');
  ENABLED = true;

  var editor = atom.workspace.getActiveTextEditor();

  // do nothing if there is no editor
  if (! editor) { return; }

  //editor.onDidChange(banana);
  editor.onDidStopChanging(banana);
}

// disables parinfer
function disable() {
  console.log('Parinfer is OFF');
  ENABLED = false;

  // TODO: unsubscribe the event from the editor
}

// toggle parinfer on / off
function toggle() {
  if (ENABLED === true) {
    disable();
  }
  else {
    enable();
  }
}

//------------------------------------------------------------------------------
// Atom-required Extension Functions
//------------------------------------------------------------------------------

function activate(_state) {
  console.log('Parinfer activated');

  subscriptions = new CompositeDisposable;
  subscriptions.add(atom.commands.add('atom-workspace', {
    'parinfer:toggle': toggle
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
