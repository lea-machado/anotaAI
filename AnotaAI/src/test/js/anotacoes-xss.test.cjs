const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const { setImmediate: nextTick } = require('node:timers/promises');
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

function notesPage(listNotes) {
  const nodes = Object.fromEntries([
    'notes-grid', 'empty-state', 'notes-count', 'notes-search', 'notes-search-input', 'notes-search-clear'
  ].map(id => [id, element()]));
  const document = {
    getElementById(id) { return nodes[id]; },
    createElement: element,
    createElementNS(_namespace, tag) { return element(tag); }
  };
  vm.runInNewContext(code, {
    document,
    Api: { listNotes },
    App: { toast() {} },
    window: { confirm: () => true },
    encodeURIComponent
  });
  return nodes;
}

test('renderiza conteúdo não confiável como texto, sem criar HTML executável', async () => {
  const payload = '<img src=x onerror=alert(1)>';
  const { 'notes-grid': grid, 'notes-count': count } = notesPage(
    async () => [{ id: '42', titulo: payload, conteudo: payload, atualizadoEm: payload }]
  );
  await nextTick();
  const card = grid.children[0];
  assert.equal(card.children[0].children[0].children[0].textContent, payload);
  assert.equal(card.children[0].children[1].textContent, payload);
  assert.equal(card.children[1].dataset.noteTitle, payload);
  assert.equal(card.children[0].href, 'nova-anotacao.html?id=42');
  assert.equal(count.textContent, '1 anotação');
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
