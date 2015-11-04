var CompositeDisposable = require('atom').CompositeDisposable,
    subscriptions = null,
    ENABLED = false;

var parinfer = require('./parinfer.js');



//------------------------------------------------------------------------------
// Run Parinfer on the TextEditor instance
//------------------------------------------------------------------------------

var LAST_TXT = null;

function banana(a) {
  //console.log("Banana!");
  //console.log(a);

  var editor = atom.workspace.getActiveTextEditor();
  if (! editor) return;
  var txt = editor.getText();

  // do nothing if the new text is the same as the result of the last parinfer run
  // NOTE: this is incredibly important because it stops an infinite loop between
  //   getText --> setText --> getText --> setText, etc
  if (txt === LAST_TXT) return;

  var newTxt = parinfer.parinfer.formatText(txt);
  if (newTxt) {
    setTimeout(function() { editor.setText(newTxt); }, 100);
    //setTimeout(function() { editor.setText('silly'); }, 100);
    LAST_TXT = newTxt;
  }
}

function run() {
  console.log('Running parinfer!');

  var editor = atom.workspace.getActiveTextEditor();
  if (! editor) return;
  var txt = editor.getText();

  var newTxt = parinfer.parinfer.formatText(txt);
  if (newTxt) {
    editor.setText(newTxt);
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

  editor.onDidChange(banana);
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
    'parinfer:run': run,
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
