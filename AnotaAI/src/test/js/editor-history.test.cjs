const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const code = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/editor.js'), 'utf8');

async function editor(versions = [], search = '') {
  const elements = new Map();
  function element(id) {
    if (!elements.has(id)) {
      const classes = new Set();
      elements.set(id, { value: '', textContent: '', dataset: {}, events: {},
        classList: { add: c => classes.add(c), remove: c => classes.delete(c), contains: c => classes.has(c) },
        addEventListener(name, fn) { const previous = this.events[name]; this.events[name] = (...args) => { if (previous) previous(...args); return fn(...args); }; },
        querySelector() { return element(id + '-small'); },
        setAttribute() {}, scrollIntoView() {} });
    }
    return elements.get(id);
  }
  const original = element('original'), revised = element('revisada');
  original.dataset.version = 'original'; revised.dataset.version = 'revisada';
  original.classList.add('active');
  const saves = [];
  const api = {
    getNote: async () => ({ titulo: 'Título', conteudo: 'Original salvo' }),
    getVersions: async () => versions,
    generateStudyMaterial: async () => ({ resumo: 'Resumo gerado pela IA' }),
    createNote: async payload => { saves.push(payload); return { id: 42, ...payload }; },
    updateNote: async (id, payload) => { saves.push(payload); return { id, ...payload }; }
  };
  await vm.runInNewContext(code, { document: {
    getElementById: element,
    querySelector: selector => selector.includes('data-version') ? revised : [original, revised].find(e => e.classList.contains('active')),
    querySelectorAll: () => [original, revised]
  }, location: { search, pathname: '/pages/nova-anotacao.html' },
    history: { replaceState() {} }, URLSearchParams, setTimeout,
    Api: api, App: { toast() {} } });
  return { element, api, saves, click: id => element(id).events.click() };
}

test('mostra a revisão gerada, mantém seleção ao reabrir e salva sem trocar o original', async () => {
  const e = await editor();
  e.element('note-title').value = 'Título';
  e.element('note-content').value = 'Meu texto';
  await e.click('generate-summary');
  assert.equal(e.element('note-content').value, 'Resumo gerado pela IA');
  await e.click('versions');
  await e.click('original');
  assert.equal(e.element('drawer-preview-text').textContent, 'Meu texto');
  await e.click('versions');
  await e.click('revisada');
  assert.equal(e.element('drawer-preview-text').textContent, 'Resumo gerado pela IA');
  await e.click('versions');
  assert.equal(e.element('drawer-preview-text').textContent, 'Resumo gerado pela IA');
  await e.click('save-note');
  assert.equal(e.saves[0].conteudo, 'Resumo gerado pela IA');
  assert.equal(e.saves[0].conteudoOriginal, 'Meu texto');
  assert.equal(e.saves[0].revisaoIa, 'Resumo gerado pela IA');
  await e.click('save-note');
  assert.equal(e.saves[1].revisaoIa, null);
});

test('ao reabrir a anotação, exibe a última revisão salva no histórico', async () => {
  const e = await editor([
    { tipo: 'ORIGINAL', conteudo: 'Primeiro original' },
    { tipo: 'REVISADA_IA', conteudo: 'Revisão antiga' },
    { tipo: 'REVISADA_IA', conteudo: 'Revisão mais recente' }
  ], '?id=42');
  await e.click('versions');
  await e.click('revisada');
  assert.equal(e.element('drawer-preview-text').textContent, 'Revisão mais recente');
  await e.click('original');
  assert.equal(e.element('drawer-preview-text').textContent, 'Primeiro original');
});

test('não associa uma resposta que chegou depois de o texto ter sido editado', async () => {
  const e = await editor();
  e.element('note-content').value = 'Texto antes';
  let resolve;
  e.api.generateStudyMaterial = () => new Promise(r => { resolve = r; });
  const pending = e.click('generate-summary');
  e.element('note-content').value = 'Texto depois';
  e.element('note-content').events.input();
  resolve({ resumo: 'Revisão do texto antigo' });
  await pending;
  await e.click('revisada');
  assert.match(e.element('drawer-preview-text').textContent, /ainda não foi gerada/);
});
