const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { setImmediate } = require('node:timers/promises');

const staticDir = path.join(__dirname, '../../main/resources/static');
const script = filename => fs.readFileSync(path.join(staticDir, 'js', filename), 'utf8');

function elementos() {
  const elements = new Map();
  function get(id) {
    if (!elements.has(id)) {
      elements.set(id, {
        textContent: '', value: '', checked: false, disabled: true, hidden: true,
        handlers: {},
        addEventListener(event, listener) { this.handlers[event] = listener; },
        replaceChildren(...children) { this.children = children; }
      });
    }
    return elements.get(id);
  }
  return { get, document: { getElementById: get, createElement: () => ({ textContent: '' }) } };
}

test('aceite obrigatório envia uma única versão ao backend', async () => {
  const { get, document } = elementos();
  const requests = [];
  const location = { replace(url) { this.destination = url; } };
  const Api = {
    getLegalStatus: async () => ({ precisaAceitar: true, aceitou: false, versao: 'v2' }),
    getCurrentLegalDocuments: async () => ({ versao: 'v2', conteudo: 'Documento único', formato: 'TEXTO' }),
    acceptLegalTerms: async data => { requests.push(data); return { precisaAceitar: false }; },
    getCurrentUser: async () => ({ perfil: 'USER' })
  };
  vm.runInNewContext(script('aceite-termos.js'), { document, Api, location });
  await setImmediate();
  assert.equal(get('review-terms-version').textContent, 'v2');
  assert.equal(get('review-accept').disabled, true);
  get('review-terms-check').checked = true;
  get('review-terms-check').handlers.change();
  assert.equal(get('review-accept').disabled, false);
  await get('review-accept').handlers.click();
  assert.deepEqual(JSON.parse(JSON.stringify(requests)), [{ aceitou: true, versao: 'v2' }]);
  assert.equal(location.destination, '/pages/anotacoes.html');
});

test('painel publica apenas o documento unificado', async () => {
  const { get, document } = elementos();
  const documentLegal = {
    id: 'doc1', versao: '2026-09-25', conteudo: 'Documento de exemplo', formato: 'TEXTO',
    publicadoEm: '2026-09-25T00:00:00'
  };
  const published = [];
  const location = { replace(url) { this.destination = url; } };
  const Api = {
    getCurrentLegalDocuments: async () => documentLegal,
    getLegalVersions: async () => [documentLegal],
    publishLegalTerms: async data => { published.push(data); }
  };
  document.createElement = () => ({
    type: '', textContent: '', addEventListener() {}
  });
  get('admin-legal-versions').replaceChildren = () => {};
  get('admin-legal-versions').append = () => {};
  vm.runInNewContext(script('admin-termos.js'), { document, Api, location });
  await setImmediate();
  get('admin-legal-text').value = 'Novo documento legal unificado. '.repeat(5);
  await get('admin-legal-form').handlers.submit({ preventDefault() {} });
  assert.deepEqual(JSON.parse(JSON.stringify(published)), [{ conteudo: 'Novo documento legal unificado. '.repeat(5).trim() }]);
  assert.equal(location.destination, '/pages/aceite-termos.html');
});