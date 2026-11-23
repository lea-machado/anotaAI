const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { setImmediate } = require('node:timers/promises');

const script = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/admin.js'), 'utf8');

async function adminPage(users = [], currentUserId = 1) {
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
      getCurrentUser: async () => ({ id: currentUserId }),
      getAdminUsers: async () => users,
      getAdminDeletionRequests: async () => [],
      getAdminSummary: async () => ({ usuarios: 0 }),
      getAdminAudit: async query => {
        queries.push(query);
        return new URLSearchParams(query).get('sucesso') === 'false' ? [] : [{
          criadoEm: '2026-11-15T23:59:00', acao: null, email: 'joa***@example.com', usuarioId: 'ae3ef3f1-654c-4326-a2db-a81a8c9c9937', enderecoIp: '203.0.113.7', sucesso: true
        }];
      }
    }
  });
  await setImmediate();
  return { get, queries };
}

test('solicita a auditoria do período selecionado e exibe registros sem ação', async () => {
  const { get, queries } = await adminPage();
  assert.match(get('audit-body').innerHTML, /ae3ef3f1….*—.*joa\*\*\*@example\.com.*Sucesso/);
  assert.equal(queries[0], 'inicio=2026-11-15&fim=2026-11-15');
  assert.match(get('audit-body').innerHTML, /title="ae3ef3f1-654c-4326-a2db-a81a8c9c9937"/);
  assert.doesNotMatch(get('audit-body').innerHTML, /203\.0\.113\.7/);
  get('audit-search').value = 'ae3ef3f1';
  await get('audit-search').handlers.input();
  assert.match(get('audit-body').innerHTML, /joa\*\*\*@example\.com/);
  get('audit-search').value = '';

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

test('identificadores UUID são preservados nas ações e na proteção da própria conta', async () => {
  const id = 'ae3ef3f1-654c-4326-a2db-a81a8c9c9937';
  const { get } = await adminPage([{ id, nome: 'Administrador', email: 'admin@example.com', perfil: 'ADMIN' }], id);
  const html = get('users-body').innerHTML;
  assert.match(html, new RegExp('data-user-id="' + id + '"'));
  assert.match(html, /disabled/);
  assert.doesNotMatch(html, /data-user-id="NaN"/);
});


test('clicar no e-mail revela o endereço e clicar novamente volta a mascarar', async () => {
  const id = 'ae3ef3f1-654c-4326-a2db-a81a8c9c9937';
  const { get } = await adminPage([{ id, nome: 'Pessoa', email: 'carlos.franca@gmail.com', perfil: 'USER' }]);
  assert.match(get('users-body').innerHTML, /data-email-user-id=/);
  assert.doesNotMatch(get('users-body').innerHTML, /carlos\.franca@gmail\.com/);
  const attributes = { 'aria-pressed': 'false' };
  const button = {
    dataset: { emailUserId: id }, textContent: 'car***@gmail.com',
    getAttribute: key => attributes[key], setAttribute: (key, value) => { attributes[key] = value; }
  };
  const event = { target: { closest: selector => selector === '[data-email-user-id]' ? button : null } };
  await get('users-body').handlers.click(event);
  assert.equal(button.textContent, 'carlos.franca@gmail.com');
  assert.equal(attributes['aria-pressed'], 'true');
  assert.equal(attributes['aria-label'], 'Ocultar e-mail completo');
  await get('users-body').handlers.click(event);
  assert.equal(button.textContent, 'car***@gmail.com');
  assert.equal(attributes['aria-pressed'], 'false');
});

test('conta anonimizada não tem botão para revelar o e-mail', async () => {
  const { get } = await adminPage([{ id: 'conta-removida', nome: 'Usuário anonimizado',
    email: 'anonimizado', perfil: 'USER', anonimizado: true }]);
  assert.doesNotMatch(get('users-body').innerHTML, /data-email-user-id=/);
  assert.match(get('users-body').innerHTML, /anonimizado/);
});
