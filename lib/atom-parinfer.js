var CompositeDisposable = require('atom').CompositeDisposable,
    subscriptions = null,
    ENABLED = false;

function activate(_state) {
  console.log('parinfer activated!');

  subscriptions = new CompositeDisposable;
  subscriptions.add(atom.commands.add('atom-workspace', {
    'parinfer:test': test,
    'parinfer:toggle': toggle
  }));
}

function deactivate() {
  subscriptions.dispose();
}

function serialize() {
  // noop
}

function toggle(evt) {
  ENABLED = ! ENABLED;

  if (ENABLED) {
    console.log("Parinfer is ON");
  }
  else {
    console.log("Parinfer is OFF");
  }
}

function test() {
  console.log("test!");
  return;

  var editor = atom.workspace.getActiveTextEditor();

  // do nothing if there is no editor
  if (! editor) { return; }

  var words = editor.getText();
  editor.setText(words + '   ZZZZZZZZZZZZZZ!!!');
}

//------------------------------------------------------------------------------
// Atom-required Main Export
//------------------------------------------------------------------------------

module.exports = {
  activate: activate,
  deactivate: deactivate,
  serialize: serialize
};
