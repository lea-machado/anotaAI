const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const script = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/perfil.js'), 'utf8');

async function abrirPerfil(escolaridade) {
  const elements = new Map();
  const get = id => {
    if (!elements.has(id)) {
      elements.set(id, {
        value: '',
        events: {},
        classList: { add() {} },
        addEventListener(event, handler) { this.events[event] = handler; }
      });
    }
    return elements.get(id);
  };
  const user = { nome: 'Ana', email: 'ana@example.com', perfil: 'USER', criadoEm: '2025-03-10', escolaridade };
  const requests = [];
  await vm.runInNewContext(script, {
    document: { getElementById: get },
    Api: {
      getCurrentUser: async () => user,
      getAudit: async () => [],
      getPerformance: async () => ({ anotacoes: 0 }),
      updateCurrentUser: async data => {
        const payload = JSON.parse(JSON.stringify(data));
        requests.push(payload);
        return { ...user, ...payload };
      }
    }
  });
  return { get, requests, salvar: () => get('profile-form').events.submit({ preventDefault() {} }) };
}

test('exibe a escolaridade salva e envia a alteração pelo perfil', async () => {
  const perfil = await abrirPerfil('Ensino Médio Completo');
  const campo = perfil.get('escolaridade');
  assert.equal(campo.value, 'Ensino Médio Completo');

  campo.value = 'Mestrado';
  await perfil.salvar();
  assert.equal(perfil.requests[0].escolaridade, 'Mestrado');
  assert.equal(campo.value, 'Mestrado');
  assert.equal(perfil.get('profile-feedback').textContent, 'Perfil atualizado com sucesso.');
});

test('conta sem escolaridade pode salvar os demais dados', async () => {
  const perfil = await abrirPerfil(null);
  assert.equal(perfil.get('escolaridade').value, '');
  await perfil.salvar();
  assert.equal(Object.hasOwn(perfil.requests[0], 'escolaridade'), false);
});