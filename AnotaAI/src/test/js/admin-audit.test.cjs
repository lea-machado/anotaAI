const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { setImmediate } = require('node:timers/promises');

const script = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/admin.js'), 'utf8');

test('solicita a auditoria do período selecionado e exibe registros sem ação', async () => {
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
      getElementById: get, addEventListener() {}, querySelectorAll: () => [],
      createElement: () => ({ get innerHTML() { return this.textContent; } })
    },
    Api: {
      getCurrentUser: async () => ({ id: 1 }),
      getAdminUsers: async () => [],
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
  assert.match(get('audit-body').innerHTML, /Conta 1.*—.*Sucesso/);
  assert.equal(queries[0], 'inicio=2026-11-15&fim=2026-11-15');

  get('audit-status-filter').value = 'failure';
  await get('audit-status-filter').handlers.change();
  assert.equal(queries[1], 'inicio=2026-11-15&fim=2026-11-15&sucesso=false');
  assert.match(get('audit-body').innerHTML, /Nenhum evento encontrado/);
});
