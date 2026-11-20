const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { setImmediate } = require('node:timers/promises');

const staticDir = path.join(__dirname, '../../main/resources/static');
const scripts = ['api.js', 'main.js'].map(file => fs.readFileSync(path.join(staticDir, 'js', file), 'utf8'));

async function navigation(page, perfil) {
  const append = [];
  let requests = 0;
  const nav = {
    querySelector(selector) {
      return selector.includes('flashcards.html') || page === 'admin' ? {} : null;
    },
    querySelectorAll: () => [],
    appendChild: link => append.push(link)
  };
  const context = vm.createContext({
    document: {
      body: { dataset: { page } },
      querySelectorAll: () => [nav],
      createElement: () => ({}),
      addEventListener() {}
    },
    fetch: async url => {
      assert.equal(url, '/api/auth/me');
      requests++;
      return { ok: true, json: async () => ({ perfil }) };
    }
  });
  context.window = context;
  scripts.forEach(script => vm.runInContext(script, context));
  if (page === 'perfil') await context.Api.getCurrentUser();
  await setImmediate();
  return { links: append, requests };
}

test('mostra Administração ao abrir cada página interna com perfil de administrador', async () => {
  for (const page of ['anotacoes', 'flashcards', 'desempenho', 'editor', 'perfil']) {
    const { links, requests } = await navigation(page, 'ADMIN');
    assert.equal(links.length, 1, page);
    assert.equal(links[0].href, '/pages/admin.html');
    assert.equal(requests, 1);
  }
});

test('oculta Administração para usuário comum e não duplica o link da página administrativa', async () => {
  assert.equal((await navigation('anotacoes', 'USER')).links.length, 0);
  assert.deepEqual(await navigation('admin', 'ADMIN'), { links: [], requests: 0 });
});