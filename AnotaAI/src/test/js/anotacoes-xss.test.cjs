const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const { setImmediate: nextTick } = require('node:timers/promises');
const dialog = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/delete-dialog.js'), 'utf8');
const code = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/anotacoes.js'), 'utf8');

function element(tag = 'div') {
  return {
    tag, children: [], dataset: {}, attributes: {}, events: {}, hidden: false, textContent: '', value: '',
    append(...nodes) { this.children.push(...nodes); },
    replaceChildren(...nodes) { this.children = [...nodes]; },
    setAttribute(name, value) { this.attributes[name] = value; },
    addEventListener(name, callback) { this.events[name] = callback; },
    focus() {},
    set innerHTML(value) { throw new Error('Uso inseguro de innerHTML: ' + value); }
  };
}

function notesPage(listNotes, deleteNote) {
  const nodes = Object.fromEntries([
    'notes-grid', 'empty-state', 'notes-count', 'notes-search', 'notes-search-input', 'notes-search-clear',
    'delete-dialog', 'delete-dialog-name', 'delete-dialog-error', 'delete-dialog-cancel', 'delete-dialog-confirm'
  ].map(id => [id, element()]));
  Object.assign(nodes['delete-dialog'], {
    open: false,
    showModal() { this.open = true; },
    close() { this.open = false; }
  });
  const document = {
    getElementById(id) { return nodes[id]; },
    createElement: element,
    createElementNS(_namespace, tag) { return element(tag); }
  };
  vm.runInNewContext(dialog + '\n' + code, {
    document,
    Api: { listNotes, deleteNote },
    App: { toast() {} },
    encodeURIComponent
  });
  return nodes;
}

function openDeleteDialog(grid) {
  const button = grid.children[0].children[1];
  grid.events.click({ target: { closest: () => button }, preventDefault() {}, stopPropagation() {} });
}

test('renderiza conteúdo não confiável como texto, sem criar HTML executável', async () => {
  const payload = '<img src=x onerror=alert(1)>';
  const { 'notes-grid': grid, 'notes-count': count, 'delete-dialog-name': deleteName } = notesPage(
    async () => [{ id: '42', titulo: payload, conteudo: payload, atualizadoEm: payload }]
  );
  await nextTick();
  const card = grid.children[0];
  assert.equal(card.children[0].children[0].children[0].textContent, payload);
  assert.equal(card.children[0].children[1].textContent, payload);
  assert.equal(card.children[1].dataset.noteTitle, payload);
  assert.equal(card.children[0].href, 'nova-anotacao.html?id=42');
  assert.equal(count.textContent, '1 anotação');
  openDeleteDialog(grid);
  assert.equal(deleteName.textContent, payload);
});

test('cancelar preserva a anotação e confirmar atualiza a lista', async () => {
  let notes = [{ id: 42, titulo: 'Semana de Arte Moderna' }];
  const deleted = [];
  const page = notesPage(async () => notes, async id => {
    deleted.push(id);
    notes = [];
  });
  const dialog = page['delete-dialog'];
  await nextTick();
  openDeleteDialog(page['notes-grid']);
  assert.equal(dialog.open, true);
  page['delete-dialog-cancel'].events.click();
  assert.equal(dialog.open, false);
  assert.deepEqual(deleted, []);

  openDeleteDialog(page['notes-grid']);
  await page['delete-dialog-confirm'].events.click();
  assert.deepEqual(deleted, [42]);
  assert.equal(dialog.open, false);
  await nextTick();
  assert.equal(page['empty-state'].hidden, false);
});

test('impede confirmação duplicada e permite tentar novamente após um erro', async () => {
  let rejectDelete;
  let requests = 0;
  const page = notesPage(async () => [{ id: 42 }], () => {
    requests += 1;
    return new Promise((resolve, reject) => { rejectDelete = reject; });
  });
  const dialog = page['delete-dialog'];
  const confirm = page['delete-dialog-confirm'];
  await nextTick();
  openDeleteDialog(page['notes-grid']);
  const deletion = confirm.events.click();
  await confirm.events.click();
  assert.equal(requests, 1);
  assert.equal(page['delete-dialog-cancel'].disabled, true);
  let prevented = false;
  dialog.events.cancel({ preventDefault() { prevented = true; } });
  assert.equal(prevented, true);

  rejectDelete(new Error('Serviço indisponível.'));
  await deletion;
  assert.equal(dialog.open, true);
  assert.equal(page['delete-dialog-error'].textContent, 'Serviço indisponível.');
  assert.equal(confirm.disabled, false);
  assert.equal(page['delete-dialog-cancel'].disabled, false);
});

test('busca por título e volta à lista completa ao limpar', async () => {
  const searches = [];
  const all = [{ id: 1, titulo: 'Química' }, { id: 2, titulo: 'Física' }];
  const page = notesPage(async search => {
    searches.push(search);
    return search ? all.filter(note => note.titulo === search) : all;
  });
  const input = page['notes-search-input'];
  const grid = page['notes-grid'];
  let prevented = false;
  const submit = () => page['notes-search'].events.submit({ preventDefault() { prevented = true; } });
  await nextTick();
  assert.equal(grid.children.length, 2);
  input.value = '  Física  ';
  submit();
  await nextTick();
  assert.ok(prevented);
  assert.equal(grid.children.length, 1);
  assert.equal(grid.children[0].children[0].href, 'nova-anotacao.html?id=2');

  input.value = 'História';
  submit();
  await nextTick();
  assert.match(page['notes-count'].textContent, /Nenhuma anotação encontrada/);
  assert.equal(page['empty-state'].hidden, true);

  page['notes-search-clear'].events.click();
  await nextTick();
  assert.equal(input.value, '');
  assert.equal(grid.children.length, 2);
  assert.deepEqual(searches, ['', 'Física', 'História', '']);
});

test('uma resposta atrasada não substitui o resultado da busca mais recente', async () => {
  const pending = new Map();
  const page = notesPage(search => new Promise(resolve => pending.set(search, resolve)));
  page['notes-search-input'].value = 'Matemática';
  page['notes-search'].events.submit({ preventDefault() {} });
  pending.get('Matemática')([{ id: 2, titulo: 'Matemática' }]);
  await nextTick();
  pending.get('')([{ id: 1, titulo: 'Química' }]);
  await nextTick();
  assert.equal(page['notes-grid'].children[0].children[0].href, 'nova-anotacao.html?id=2');
});
