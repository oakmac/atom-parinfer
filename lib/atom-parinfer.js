var CompositeDisposable = require('atom').CompositeDisposable,
    subscriptions = null,
    ENABLED = false;

var parinfer = require('./parinfer.js');

//------------------------------------------------------------------------------
// Run Parinfer on the TextEditor instance
//------------------------------------------------------------------------------

var LAST_TXT = null;

function banana(_x) {
  var editor = atom.workspace.getActiveTextEditor();
  if (! editor) return;

  var txt = editor.getText();
  var cursorPosition = editor.getCursorBufferPosition();

  // do nothing if the new text is the same as the result of the last parinfer run
  // NOTE: this is incredibly important because it stops an infinite loop between
  //   getText --> setText --> getText --> setText, etc
  if (txt === LAST_TXT) return;

  var newTxt = parinfer.parinfer.formatText(txt, cursorPosition);
  if (newTxt) {
    editor.setText(newTxt);
    editor.setCursorBufferPosition(cursorPosition);
    LAST_TXT = newTxt;
  }
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
