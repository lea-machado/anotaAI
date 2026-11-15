const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const code = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/anotacoes.js'), 'utf8');

function element(tag = 'div') {
  return {
    tag, children: [], dataset: {}, attributes: {}, hidden: false, textContent: '',
    append(...nodes) { this.children.push(...nodes); },
    replaceChildren(...nodes) { this.children = [...nodes]; },
    setAttribute(name, value) { this.attributes[name] = value; },
    addEventListener() {},
    set innerHTML(value) { throw new Error('Uso inseguro de innerHTML: ' + value); }
  };
}

test('renderiza conteúdo não confiável como texto, sem criar HTML executável', async () => {
  const grid = element('section');
  const empty = element('section');
  const count = element('span');
  const nodes = { 'notes-grid': grid, 'empty-state': empty, 'notes-count': count };
  const payload = '<img src=x onerror=alert(1)>';
  const document = {
    getElementById(id) { return nodes[id]; },
    createElement: element,
    createElementNS(_namespace, tag) { return element(tag); }
  };
  vm.runInNewContext(code, {
    document,
    Api: { listNotes: async () => [{ id: '42', titulo: payload, conteudo: payload, atualizadoEm: payload }] },
    App: { toast() {} },
    window: { confirm: () => true },
    encodeURIComponent
  });
  await new Promise(resolve => setImmediate(resolve));
  const card = grid.children[0];
  assert.equal(card.children[0].children[0].children[0].textContent, payload);
  assert.equal(card.children[0].children[1].textContent, payload);
  assert.equal(card.children[1].dataset.noteTitle, payload);
  assert.equal(card.children[0].href, 'nova-anotacao.html?id=42');
  assert.equal(count.textContent, '1 anotação');
});