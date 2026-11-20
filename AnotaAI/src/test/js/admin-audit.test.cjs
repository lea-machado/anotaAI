const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { setImmediate } = require('node:timers/promises');

const script = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/admin.js'), 'utf8');

async function adminPage(users = []) {
  const values = {
    'dashboard-period': 'custom', 'dashboard-start': '2026-11-15', 'dashboard-end': '2026-11-15',
    'audit-status-filter': 'all', 'users-role-filter': 'all', 'users-status-filter': 'all'
  };
  const elements = new Map();
  function get(id) {
    if (!elements.has(id)) {
      elements.set(id, {
        value: values[id] ?? '', style: {}, handlers: {},
        addEventListener(event, handler) { this.handlers[event] = handler; }
      });
    }
    return elements.get(id);
  }
  const queries = [];
  vm.runInNewContext(script, {
    URLSearchParams,
    document: {
      getElementById: get, addEventListener() { }, querySelectorAll: () => [],
      createElement: () => ({ get innerHTML() { return this.textContent; } })
    },
    Api: {
      getCurrentUser: async () => ({ id: 1 }),
      getAdminUsers: async () => users,
      getAdminDeletionRequests: async () => [],
      getAdminSummary: async () => ({ usuarios: 0 }),
      getAdminAudit: async query => {
        queries.push(query);
        return new URLSearchParams(query).get('sucesso') === 'false' ? [] : [{
          criadoEm: '2026-11-15T23:59:00', acao: null, email: 'Conta 1', sucesso: true
        }];
      }
    }
  });
  await setImmediate();
  return { get, queries };
}

test('solicita a auditoria do período selecionado e exibe registros sem ação', async () => {
  const { get, queries } = await adminPage();
  assert.match(get('audit-body').innerHTML, /Conta 1.*—.*Sucesso/);
  assert.equal(queries[0], 'inicio=2026-11-15&fim=2026-11-15');

  get('audit-status-filter').value = 'failure';
  await get('audit-status-filter').handlers.change();
  assert.equal(queries[1], 'inicio=2026-11-15&fim=2026-11-15&sucesso=false');
  assert.match(get('audit-body').innerHTML, /Nenhum evento encontrado/);
});

test('lista usuários de qualquer data e atualiza a lista mantendo os filtros próprios', async () => {
  const users = [
    { id: 2, nome: 'Conta recente', perfil: 'USER', criadoEm: '2026-11-15T12:00:00' },
    { id: 3, nome: 'Conta antiga', perfil: 'ADMIN', criadoEm: '2026-09-30T12:00:00' },
    { id: 4, nome: 'Conta sem data', perfil: 'USER', ativo: false }
  ];
  const { get, queries } = await adminPage(users);
  assert.match(get('users-body').innerHTML, /Conta antiga/);
  assert.match(get('users-body').innerHTML, /Conta sem data/);
  assert.equal(get('users-result-count').textContent, '3 de 3 usuários');

  get('dashboard-period').value = 'all';
  await get('dashboard-period').handlers.change();
  assert.equal(queries.at(-1), '');
  assert.equal(get('users-result-count').textContent, '3 de 3 usuários');

  get('users-role-filter').value = 'USER';
  get('users-role-filter').handlers.change();
  get('users-status-filter').value = 'active';
  get('users-status-filter').handlers.change();
  get('users-search').value = 'RECENTE';
  get('users-search').handlers.input();
  assert.equal(get('users-result-count').textContent, '1 de 3 usuários');

  users.push({ id: 5, nome: 'Cadastro recente', perfil: 'USER', criadoEm: '2026-11-16T12:00:00' });
  await get('dashboard-refresh').handlers.click();
  assert.match(get('users-body').innerHTML, /Cadastro recente/);
  assert.equal(get('users-result-count').textContent, '2 de 4 usuários');
});

test('oculta o e-mail dos usuários, mas preserva o domínio na lista administrativa', async () => {
  const users = [
    { id: 2, nome: 'Pessoa', email: 'carlos.franca@gmail.com', perfil: 'USER' },
    { id: 3, nome: 'Pessoa 2', email: 'mariana@example.org', perfil: 'USER' },
    { id: 4, nome: 'Pessoa 3', email: 'ab@outlook.com', perfil: 'USER' }
  ];
  const { get } = await adminPage(users);
  const html = get('users-body').innerHTML;
  assert.match(html, /car\*\*\*@gmail\.com/);
  assert.match(html, /mar\*\*\*@example\.org/);
  assert.match(html, /ab@outlook\.com/);
  assert.doesNotMatch(html, /carlos\.franca@gmail\.com|mariana@example\.org/);
  get('users-search').value = 'carlos.franca@gmail.com';
  get('users-search').handlers.input();
  assert.equal(get('users-result-count').textContent, '1 de 3 usuários');
});